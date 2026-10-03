package com.parkflow.backend.ai;

import com.parkflow.backend.rules.RuleDraft;
import com.parkflow.backend.rules.RuleStore;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Câu tiếng Việt thành bản nháp quy tắc để quản trị XEM TRƯỚC (docs/thiet-ke/08-ai-prompts.md mục 4). Không lưu gì.
 * Kết quả của AI luôn được kiểm tra bằng đúng bộ quy tắc hợp lệ khi lưu; sai thì dùng bộ đoán theo từ khóa (source = mock).
 */
@Service
public class RuleParser {
    static final int MAX_TEXT = 300;
    static final String SYSTEM = """
            Bạn chuyển một câu tiếng Việt của quản trị bãi xe thành MỘT quy tắc. Trả JSON đúng schema:
            {"rule":{"name":"...","type":"alert|routing","scope":"ALL hoặc mã bãi","from":"HH:mm","to":"HH:mm",
            "metric":"forecast_pct|current_pct|inflow_15m","op":">=|>|<=|<","value":0,"action":"..."},"explanation":"Nếu ... thì ..."}
            type routing chỉ khi câu muốn điều hướng hoặc chuyển xe; khi đó metric bắt buộc forecast_pct, op là >= hoặc >, value từ 50 đến 100.
            Còn lại là alert. Không bịa số: chỉ dùng số có trong câu, thiếu thì dùng mặc định 90 và khung 06:30-08:00.
            explanation là một câu tiếng Việt dạng "Nếu ... thì ...".
            """;
    private static final Pattern TIME = Pattern.compile("([0-9]{1,2})\\s*(?:h|gio|:)\\s*([0-9]{2})?");
    private static final Pattern PERCENT = Pattern.compile("([0-9]{1,3}(?:[.,][0-9]+)?)\\s*(?:%|phan tram)");
    private static final Pattern NUMBER = Pattern.compile("([0-9]{1,5})");

    private final AiService ai;
    private final AiMode mode;
    private final RuleStore rules;
    private final ObjectMapper mapper;

    public RuleParser(AiService ai, AiMode mode, RuleStore rules, ObjectMapper mapper) {
        this.ai = ai;
        this.mode = mode;
        this.rules = rules;
        this.mapper = mapper;
    }

    public ObjectNode parse(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_TEXT) throw new IllegalArgumentException("text");
        Set<String> lotIds = rules.lotIds();
        if (!mode.mock()) {
            try {
                ObjectNode raw = ai.run(SYSTEM, "CÂU: " + text.strip(), RuleParser::validShape, () -> heuristic(text, lotIds));
                if (!MockAi.SOURCE.equals(raw.path("source").asString()) && acceptable(raw, lotIds)) return raw;
            } catch (AiUnavailable e) {
                // dùng bộ đoán theo từ khóa bên dưới
            }
        }
        return heuristic(text, lotIds);
    }

    static boolean validShape(JsonNode node) {
        JsonNode rule = node.get("rule");
        return rule != null && rule.isObject() && rule.path("name").isString() && rule.path("type").isString() && rule.path("scope").isString()
                && rule.path("from").isString() && rule.path("to").isString() && rule.path("metric").isString() && rule.path("op").isString()
                && rule.path("value").isNumber() && node.path("explanation").isString();
    }

    private boolean acceptable(ObjectNode raw, Set<String> lotIds) {
        JsonNode r = raw.get("rule");
        RuleDraft draft = new RuleDraft(r.path("name").asString(), r.path("type").asString(), r.path("scope").asString(), r.path("from").asString(),
                r.path("to").asString(), r.path("metric").asString(), r.path("op").asString(), r.path("value").asDouble(), r.path("action").asString(""));
        return draft.problems(lotIds).isEmpty();
    }

    /** Đoán theo từ khóa; khung giờ, ngưỡng và phạm vi lấy từ câu, thiếu thì dùng mặc định đã nói rõ trong `explanation`. */
    ObjectNode heuristic(String text, Set<String> lotIds) {
        String plain = plain(text);
        boolean routing = containsAny(plain, "dieu huong", "chuyen xe", "phan luong", "chuyen huong");
        String metric = containsAny(plain, "xe vao", "luot vao", "15 phut") ? "inflow_15m"
                : containsAny(plain, "hien tai", "dang co", "dang day", "dang dong") ? "current_pct" : "forecast_pct";
        if (routing) metric = "forecast_pct";
        String op = containsAny(plain, "duoi", "it hon", "nho hon", "thap hon") ? "<" : ">=";
        if (routing) op = ">=";
        String scope = "ALL";
        Matcher bai = Pattern.compile("bai\\s*([a-z][0-9]?)").matcher(plain);
        if (bai.find() && lotIds.contains(bai.group(1).toUpperCase(Locale.ROOT))) scope = bai.group(1).toUpperCase(Locale.ROOT);

        double value = 90;
        boolean valueFound = false;
        Matcher percent = PERCENT.matcher(plain);
        if (percent.find()) {
            value = Double.parseDouble(percent.group(1).replace(',', '.'));
            valueFound = true;
        } else if ("inflow_15m".equals(metric)) {
            Matcher number = NUMBER.matcher(plain.replaceAll(TIME.pattern(), " "));
            if (number.find()) {
                value = Double.parseDouble(number.group(1));
                valueFound = true;
            }
        }
        if (routing) value = Math.max(50, Math.min(100, value));

        String from = "06:30";
        String to = "08:00";
        boolean timeFound = false;
        Matcher time = TIME.matcher(plain);
        List<String> found = new java.util.ArrayList<>();
        while (time.find() && found.size() < 2) {
            int hour = Integer.parseInt(time.group(1));
            int minute = time.group(2) == null ? 0 : Integer.parseInt(time.group(2));
            if (hour < 24 && minute < 60) found.add(String.format("%02d:%02d", hour, minute));
        }
        if (found.size() == 2 && found.get(0).compareTo(found.get(1)) < 0) {
            from = found.get(0);
            to = found.get(1);
            timeFound = true;
        }

        String action = routing ? "Điều hướng xe sang bãi còn chỗ" : "Thông báo điều hành";
        String name = text.strip().length() > 80 ? text.strip().substring(0, 80) : text.strip();
        ObjectNode rule = mapper.createObjectNode();
        rule.put("name", name).put("type", routing ? "routing" : "alert").put("scope", scope).put("from", from).put("to", to)
                .put("metric", metric).put("op", op).put("value", value).put("action", action);

        String label = switch (metric) {
            case "current_pct" -> "mức lấp đầy hiện tại";
            case "inflow_15m" -> "số xe vào trong 15 phút";
            default -> "mức lấp đầy dự báo";
        };
        String unit = "inflow_15m".equals(metric) ? " xe" : "%";
        StringBuilder explanation = new StringBuilder("Nếu ").append(label).append(" của ").append("ALL".equals(scope) ? "mọi bãi" : "bãi " + scope)
                .append(" ").append(op).append(" ").append(trim(value)).append(unit).append(" trong khung ").append(from).append("–").append(to)
                .append(" thì ").append(action.substring(0, 1).toLowerCase(Locale.ROOT)).append(action.substring(1)).append(".");
        if (!valueFound || !timeFound) {
            explanation.append(" (Câu không nêu").append(!valueFound ? " ngưỡng" : "").append(!valueFound && !timeFound ? " và" : "")
                    .append(!timeFound ? " khung giờ" : "").append(", đã dùng mặc định; kiểm tra lại trước khi lưu.)");
        }
        ObjectNode result = mapper.createObjectNode();
        result.set("rule", rule);
        result.put("explanation", explanation.toString());
        result.put("source", MockAi.SOURCE);
        return result;
    }

    private static boolean containsAny(String text, String... keys) {
        for (String key : keys) if (text.contains(key)) return true;
        return false;
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    static String plain(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }
}
