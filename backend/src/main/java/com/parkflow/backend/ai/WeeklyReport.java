package com.parkflow.backend.ai;

import com.parkflow.backend.common.ParkFlowClock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Báo cáo tuần dạng markdown (docs/thiet-ke/08-ai-prompts.md mục 5). Số liệu do backend tổng hợp từ sự kiện của tuần (lượt vào, lượt ra,
 * ngày đông nhất, khung 15 phút đông nhất của từng bãi); AI chỉ viết lời. Có phần trăm nào không nằm trong số liệu thì bỏ câu của AI
 * và dùng báo cáo dựng thẳng từ số liệu. Không gửi mã thẻ hay dữ liệu từng xe cho AI.
 */
@Service
@RequiredArgsConstructor
public class WeeklyReport {
    static final String SYSTEM = """
            Bạn viết báo cáo tuần cho ban quản trị bãi xe bằng tiếng Việt, ngắn gọn, định dạng markdown, đúng 4 mục có tiêu đề:
            "## Tóm tắt", "## Điểm nghẽn", "## Khung cao điểm", "## Khuyến nghị cho tuần tới".
            Chỉ dùng số trong FACTS. Không tự tính thêm, không bịa số; không viết phần trăm vì FACTS không có phần trăm.
            Thiếu dữ liệu thì nói rõ là chưa có dữ liệu. Trả JSON {"markdown":"..."}.
            """;

    private final JdbcTemplate jdbc;
    private final AiService ai;
    private final AiMode mode;
    private final ObjectMapper mapper;

    public ObjectNode report(LocalDate weekStart) {
        ObjectNode facts = facts(weekStart);
        if (!mode.mock()) {
            try {
                ObjectNode raw = ai.run(SYSTEM, "FACTS: " + mapper.writeValueAsString(facts), WeeklyReport::validShape, () -> build(facts));
                String markdown = raw.path("markdown").asString();
                if (!MockAi.SOURCE.equals(raw.path("source").asString()) && NumberGuard.onlyKnownPercentages(markdown, facts)
                        && hasAllSections(markdown)) return raw;
            } catch (AiUnavailable e) {
                // dùng báo cáo dựng từ số liệu
            }
        }
        return build(facts);
    }

    static boolean validShape(JsonNode node) {
        return node.path("markdown").isString() && !node.path("markdown").asString().isBlank() && node.path("markdown").asString().length() < 6000;
    }

    static boolean hasAllSections(String markdown) {
        return markdown.contains("## Tóm tắt") && markdown.contains("## Điểm nghẽn") && markdown.contains("## Khung cao điểm")
                && markdown.contains("## Khuyến nghị");
    }

    /** Số liệu tổng hợp của tuần bắt đầu từ `weekStart` (7 ngày, giờ Việt Nam). */
    ObjectNode facts(LocalDate weekStart) {
        OffsetDateTime from = weekStart.atStartOfDay(ParkFlowClock.ZONE).toOffsetDateTime();
        OffsetDateTime to = weekStart.plusDays(7).atStartOfDay(ParkFlowClock.ZONE).toOffsetDateTime();
        Map<String, ObjectNode> lots = new TreeMap<>();
        jdbc.query("SELECT lot_id, name, capacity FROM lots ORDER BY lot_id", rs -> {
            ObjectNode lot = mapper.createObjectNode();
            lot.put("lotId", rs.getString("lot_id")).put("name", rs.getString("name")).put("capacity", rs.getInt("capacity"));
            lot.put("entries", 0).put("exits", 0);
            lot.putArray("days");
            lots.put(rs.getString("lot_id"), lot);
        });
        Map<String, int[]> busiest = new HashMap<>();
        Map<String, String> busiestDay = new HashMap<>();
        jdbc.query("""
                SELECT lot_id, (ts AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day,
                       COUNT(*) FILTER (WHERE direction = 'in') AS entries, COUNT(*) FILTER (WHERE direction = 'out') AS exits
                FROM events WHERE ts >= ? AND ts < ? GROUP BY 1, 2 ORDER BY 1, 2
                """, rs -> {
            ObjectNode lot = lots.get(rs.getString("lot_id"));
            if (lot == null) return;
            int entries = rs.getInt("entries");
            lot.put("entries", lot.path("entries").asInt() + entries).put("exits", lot.path("exits").asInt() + rs.getInt("exits"));
            ((ArrayNode) lot.get("days")).addObject().put("date", rs.getString("day")).put("entries", entries);
            int[] best = busiest.computeIfAbsent(rs.getString("lot_id"), k -> new int[] {-1});
            if (entries > best[0]) {
                best[0] = entries;
                busiestDay.put(rs.getString("lot_id"), rs.getString("day"));
            }
        }, from, to);
        Map<String, Object[]> peaks = new HashMap<>();
        jdbc.query("""
                SELECT lot_id, to_char(bucket AT TIME ZONE 'Asia/Ho_Chi_Minh', 'HH24:MI') AS hhmm, cnt FROM (
                    SELECT lot_id, date_bin('15 minutes', ts, TIMESTAMPTZ '2000-01-01 00:00:00+00') AS bucket, COUNT(*) AS cnt
                    FROM events WHERE direction = 'in' AND ts >= ? AND ts < ? GROUP BY 1, 2) x
                ORDER BY cnt DESC
                """, rs -> {
            peaks.putIfAbsent(rs.getString("lot_id"), new Object[] {rs.getString("hhmm"), rs.getInt("cnt")});
        }, from, to);
        ArrayNode list = mapper.createArrayNode();
        int totalEntries = 0;
        for (var entry : lots.entrySet()) {
            ObjectNode lot = entry.getValue();
            String id = entry.getKey();
            if (busiestDay.containsKey(id)) lot.putObject("busiestDay").put("date", busiestDay.get(id)).put("entries", busiest.get(id)[0]);
            if (peaks.containsKey(id)) lot.putObject("peak15").put("time", (String) peaks.get(id)[0]).put("entries", (Integer) peaks.get(id)[1]);
            totalEntries += lot.path("entries").asInt();
            list.add(lot);
        }
        ObjectNode facts = mapper.createObjectNode();
        facts.putObject("week").put("start", weekStart.toString()).put("end", weekStart.plusDays(6).toString());
        facts.set("lots", list);
        facts.put("totalEntries", totalEntries);
        return facts;
    }

    /** Báo cáo dựng thẳng từ số liệu (không gọi mô hình). */
    ObjectNode build(ObjectNode facts) {
        StringBuilder md = new StringBuilder();
        JsonNode week = facts.path("week");
        int total = facts.path("totalEntries").asInt();
        md.append("**Dữ liệu mẫu.** Báo cáo tuần ").append(week.path("start").asString()).append(" đến ").append(week.path("end").asString()).append(".\n\n");
        md.append("## Tóm tắt\n");
        if (total == 0) md.append("Tuần này chưa có dữ liệu lượt vào nào.\n");
        else md.append("Tổng ").append(total).append(" lượt xe vào trong tuần, trên ").append(facts.path("lots").size()).append(" bãi.\n");
        List<JsonNode> ranked = new ArrayList<>();
        facts.path("lots").forEach(ranked::add);
        ranked.sort((a, b) -> Integer.compare(b.path("entries").asInt(), a.path("entries").asInt()));
        md.append("\n## Điểm nghẽn\n");
        if (ranked.isEmpty() || ranked.get(0).path("entries").asInt() == 0) md.append("Chưa có dữ liệu để xác định bãi đông nhất.\n");
        else {
            JsonNode top = ranked.get(0);
            md.append("Bãi ").append(top.path("lotId").asString()).append(" đông nhất với ").append(top.path("entries").asInt()).append(" lượt vào");
            if (top.has("busiestDay")) md.append(", đông nhất vào ngày ").append(top.path("busiestDay").path("date").asString()).append(" (")
                    .append(top.path("busiestDay").path("entries").asInt()).append(" lượt vào)");
            md.append(".\n");
        }
        md.append("\n## Khung cao điểm\n");
        boolean any = false;
        for (JsonNode lot : ranked) {
            if (!lot.has("peak15")) continue;
            any = true;
            md.append("- Bãi ").append(lot.path("lotId").asString()).append(": khung 15 phút đông nhất bắt đầu lúc ").append(lot.path("peak15").path("time").asString())
                    .append(" với ").append(lot.path("peak15").path("entries").asInt()).append(" lượt vào.\n");
        }
        if (!any) md.append("Chưa có dữ liệu về khung cao điểm.\n");
        md.append("\n## Khuyến nghị cho tuần tới\n");
        if (any) md.append("Chuẩn bị bảo vệ và mở cổng sớm trước các khung cao điểm nêu trên; phương án cần quản trị duyệt trước khi áp dụng.\n");
        else md.append("Cần thêm dữ liệu trước khi đề xuất; phương án cần quản trị duyệt trước khi áp dụng.\n");
        ObjectNode result = mapper.createObjectNode();
        result.put("markdown", md.toString());
        result.put("source", MockAi.SOURCE);
        return result;
    }
}
