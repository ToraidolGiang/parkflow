package com.parkflow.backend.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Ánh xạ cột của tệp người dùng vào trường hệ thống (docs/thiet-ke/08-ai-prompts.md mục 3).
 * AI chỉ nhận TÊN CỘT và vài giá trị mẫu ngắn; cột trông như mã thẻ thì không gửi giá trị mẫu nào.
 * Kết quả luôn có đúng một dòng cho mỗi cột, mỗi trường hệ thống tối đa một cột; độ tin cậy dưới 0,8 để giao diện bắt người dùng kiểm tra.
 */
@Service
public class ColumnMapper {
    static final Map<String, List<String>> FIELDS = Map.of(
            "swipe_log", List.of("time_in", "time_out", "gate", "card_id", "lot", "vehicle_type"),
            "schedule", List.of("class_id", "building", "date", "start_time", "end_time", "students"));
    static final int MAX_COLUMNS = 40;
    static final int MAX_SAMPLES = 5;
    static final int MAX_SAMPLE_LENGTH = 40;

    static final String SYSTEM = """
            Bạn ánh xạ cột của file dữ liệu bãi xe vào trường hệ thống ParkFlow.
            Trường hệ thống cho log quẹt thẻ: time_in, time_out, gate, card_id, lot, vehicle_type.
            Trường hệ thống cho lịch học: class_id, building, date, start_time, end_time, students.
            Dựa vào tên cột VÀ giá trị mẫu. Mỗi cột trả đúng một trường hoặc "ignore".
            confidence từ 0 đến 1; giá trị viết tắt hoặc mơ hồ thì confidence < 0.8. Trả JSON đúng schema:
            {"mappings":[{"column":"...","field":"...","confidence":0.0,"note":"..."}]}
            """;

    /** Tên cột (bỏ dấu, chữ thường, chỉ chữ và số) cho từng trường; dùng cho dữ liệu mẫu khi AI không dùng được. */
    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("time_in", List.of("timein", "thoigianvao", "giovao", "entrytime", "checkin", "vao", "in", "timestamp")),
            Map.entry("time_out", List.of("timeout", "thoigianra", "giora", "exittime", "checkout", "ra", "out")),
            Map.entry("gate", List.of("gate", "cong", "tencong")),
            Map.entry("card_id", List.of("cardid", "card", "mathe", "sothe", "cardhash", "uid")),
            Map.entry("lot", List.of("lot", "lotid", "bai", "baixe", "mabai", "parking")),
            Map.entry("vehicle_type", List.of("vehicletype", "loaixe", "loai", "vehicle", "type")),
            Map.entry("class_id", List.of("classid", "malop", "lop", "class", "mahocphan")),
            Map.entry("building", List.of("building", "toanha", "nha", "phonghoc", "phong")),
            Map.entry("date", List.of("date", "ngay", "ngayhoc")),
            Map.entry("start_time", List.of("starttime", "giobatdau", "batdau", "bd", "from")),
            Map.entry("end_time", List.of("endtime", "gioketthuc", "ketthuc", "kt", "to")),
            Map.entry("students", List.of("students", "sosinhvien", "siso", "sosv", "sv")));

    private final AiService ai;
    private final AiMode mode;
    private final ObjectMapper mapper;

    public ColumnMapper(AiService ai, AiMode mode, ObjectMapper mapper) {
        this.ai = ai;
        this.mode = mode;
        this.mapper = mapper;
    }

    /** `request`: `{kind: "swipe_log"|"schedule", columns: [{name, samples: [..]}]}`. */
    public ObjectNode map(JsonNode request) {
        String kind = request.path("kind").asString("swipe_log");
        List<String> fields = FIELDS.get(kind);
        if (fields == null) throw new IllegalArgumentException("kind");
        JsonNode columns = request.path("columns");
        if (!columns.isArray() || columns.isEmpty() || columns.size() > MAX_COLUMNS) throw new IllegalArgumentException("columns");

        List<String> names = new ArrayList<>();
        ObjectNode prompt = mapper.createObjectNode();
        prompt.put("kind", kind);
        ArrayNode shown = prompt.putArray("columns");
        for (JsonNode column : columns) {
            String name = column.path("name").asString("").strip();
            if (name.isEmpty() || name.length() > 80) throw new IllegalArgumentException("columns");
            names.add(name);
            ObjectNode out = shown.addObject();
            out.put("name", name);
            ArrayNode samples = out.putArray("samples");
            if (!looksLikeCardColumn(name, fields)) {
                int count = 0;
                for (JsonNode sample : column.path("samples")) {
                    if (count++ >= MAX_SAMPLES) break;
                    String text = sample.asString("");
                    samples.add(text.length() > MAX_SAMPLE_LENGTH ? text.substring(0, MAX_SAMPLE_LENGTH) : text);
                }
            }
        }

        ObjectNode raw;
        if (mode.mock()) {
            raw = heuristic(names, fields);
        } else {
            try {
                raw = ai.run(SYSTEM, "DỮ LIỆU: " + mapper.writeValueAsString(prompt), ColumnMapper::validShape, () -> heuristic(names, fields));
            } catch (AiUnavailable e) {
                raw = e.fallback();
            }
        }
        return normalize(raw, names, fields);
    }

    /** Cột chứa mã thẻ: không bao giờ gửi giá trị mẫu cho AI. */
    private static boolean looksLikeCardColumn(String name, List<String> fields) {
        String key = key(name);
        return key.contains("card") || key.contains("mathe") || key.contains("sothe") || key.equals("uid") || key.contains("hash");
    }

    static boolean validShape(JsonNode node) {
        JsonNode mappings = node.get("mappings");
        if (mappings == null || !mappings.isArray()) return false;
        for (JsonNode item : mappings) {
            if (!item.path("column").isString() || !item.path("field").isString()) return false;
            if (!item.path("confidence").isNumber()) return false;
        }
        return true;
    }

    /** Một dòng cho mỗi cột theo đúng thứ tự; trường lạ thành ignore; độ tin cậy kẹp [0,1]; trường trùng chỉ giữ cột tin cậy hơn. */
    ObjectNode normalize(ObjectNode raw, List<String> names, List<String> fields) {
        Map<String, JsonNode> byColumn = new HashMap<>();
        for (JsonNode item : raw.path("mappings")) byColumn.putIfAbsent(item.path("column").asString(), item);

        Map<String, double[]> best = new HashMap<>();   // trường -> {chỉ số cột, độ tin cậy}
        List<String> chosenField = new ArrayList<>();
        List<Double> confidence = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            JsonNode item = byColumn.get(names.get(i));
            String field = item == null ? "ignore" : item.path("field").asString("ignore");
            double conf = item == null ? 0 : Math.max(0, Math.min(1, item.path("confidence").asDouble(0)));
            String note = item == null ? "Không có gợi ý cho cột này." : item.path("note").asString("");
            if (!fields.contains(field)) {
                if (!"ignore".equals(field)) note = "Trường \"" + field + "\" không thuộc hệ thống, bỏ qua. " + note;
                field = "ignore";
            }
            chosenField.add(field);
            confidence.add(conf);
            notes.add(note.strip());
            if (!"ignore".equals(field)) {
                double[] current = best.get(field);
                if (current == null || conf > current[1]) best.put(field, new double[] {i, conf});
            }
        }
        ArrayNode mappings = mapper.createArrayNode();
        for (int i = 0; i < names.size(); i++) {
            String field = chosenField.get(i);
            double conf = confidence.get(i);
            String note = notes.get(i);
            if (!"ignore".equals(field) && (int) best.get(field)[0] != i) {
                note = "Trùng trường \"" + field + "\" với cột có độ tin cậy cao hơn, bỏ qua. " + note;
                field = "ignore";
                conf = Math.min(conf, 0.3);
            }
            ObjectNode out = mappings.addObject();
            out.put("column", names.get(i));
            out.put("field", field);
            out.put("confidence", Math.round(conf * 100) / 100.0);
            out.put("note", note.strip());
            out.put("needsReview", conf < 0.8);
        }
        ObjectNode result = mapper.createObjectNode();
        result.set("mappings", mappings);
        result.put("source", raw.path("source").asString(MockAi.SOURCE));
        result.put("needsReview", mappings.valueStream().anyMatch(m -> m.path("needsReview").asBoolean()));
        return result;
    }

    /** Dự phòng không cần AI: đoán theo tên cột. Khớp đúng tên thì tin cậy cao, khớp một phần thì thấp và bắt người dùng kiểm tra. */
    ObjectNode heuristic(List<String> names, List<String> fields) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode mappings = result.putArray("mappings");
        for (String name : names) {
            String key = key(name);
            String field = "ignore";
            double conf = 0.2;
            String note = "Không nhận ra cột này.";
            for (String candidate : fields) {
                List<String> words = SYNONYMS.getOrDefault(candidate, List.of());
                if (key.equals(candidate.replace("_", "")) || words.contains(key)) {
                    field = candidate; conf = 0.95; note = "Tên cột khớp trường."; break;
                }
                if (words.stream().anyMatch(word -> word.length() >= 3 && key.contains(word)) && conf < 0.6) {
                    field = candidate; conf = 0.6; note = "Tên cột gần giống trường, cần kiểm tra.";
                }
            }
            mappings.addObject().put("column", name).put("field", field).put("confidence", conf).put("note", note);
        }
        result.put("source", MockAi.SOURCE);
        return result;
    }

    static String key(String name) {
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
        return plain.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
