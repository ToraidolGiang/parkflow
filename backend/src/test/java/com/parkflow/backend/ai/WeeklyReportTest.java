package com.parkflow.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Báo cáo tuần: số liệu tổng hợp từ sự kiện, báo cáo dựng từ số liệu, chặn phần trăm bịa. Cần Postgres; dọn dữ liệu của chính test. */
@SpringBootTest
class WeeklyReportTest {
    private static final LocalDate WEEK = LocalDate.parse("2030-01-07");
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @BeforeEach void setup() {
        cleanup();
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate) VALUES ('WR1', 'Bãi báo cáo', 100, 120, 'MAIN')");
        for (int i = 0; i < 5; i++) insert("a" + i, "2030-01-07T07:0" + i + ":00+07:00", "in");
        for (int i = 0; i < 3; i++) insert("b" + i, "2030-01-08T08:0" + i + ":00+07:00", "in");
        insert("c0", "2030-01-07T17:00:00+07:00", "out");
        insert("old", "2029-12-31T07:00:00+07:00", "in");   // ngoài tuần
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM events WHERE lot_id = 'WR1'");
        jdbc.update("DELETE FROM lots WHERE lot_id = 'WR1'");
    }

    private void insert(String id, String ts, String direction) {
        jdbc.update("INSERT INTO events (event_id, ts, lot_id, direction, vehicle_type, card_hash) VALUES (?, ?::timestamptz, 'WR1', ?, 'car', ?)",
                "wr-" + id, ts, direction, "h-" + id);
    }

    private WeeklyReport report(boolean mock, AiService ai) { return new WeeklyReport(jdbc, ai, new AiMode(mock), mapper); }

    @Test void factsAggregateOnlyTheWeekWithEntriesExitsBusiestDayAndPeakWindow() {
        var lot = report(true, mock(AiService.class)).facts(WEEK).path("lots");
        var wr1 = java.util.stream.StreamSupport.stream(lot.spliterator(), false).filter(l -> "WR1".equals(l.path("lotId").asString())).findFirst().orElseThrow();
        assertEquals(8, wr1.path("entries").asInt(), "5 + 3, không tính ngày ngoài tuần");
        assertEquals(1, wr1.path("exits").asInt());
        assertEquals("2030-01-07", wr1.path("busiestDay").path("date").asString());
        assertEquals(5, wr1.path("busiestDay").path("entries").asInt());
        assertEquals("07:00", wr1.path("peak15").path("time").asString());
        assertEquals(5, wr1.path("peak15").path("entries").asInt());
        assertFalse(wr1.toString().contains("h-a"), "không có mã thẻ trong số liệu");
    }

    @Test void theBuiltReportHasFourSectionsWithRealNumbers() {
        var result = report(true, mock(AiService.class)).report(WEEK);
        String md = result.path("markdown").asString();
        assertTrue(WeeklyReport.hasAllSections(md), md);
        assertTrue(md.contains("Bãi WR1 đông nhất") || md.contains("lượt vào"), md);
        assertTrue(md.contains("07:00"), md);
        assertEquals("mock", result.path("source").asString());
    }

    @Test void aWeekWithoutDataSaysSoInsteadOfInventingNumbers() {
        String md = report(true, mock(AiService.class)).report(LocalDate.parse("2031-01-06")).path("markdown").asString();
        assertTrue(md.contains("chưa có dữ liệu"), md);
        assertTrue(WeeklyReport.hasAllSections(md));
    }

    @Test void aModelReportWithAnInventedPercentageIsReplacedByTheBuiltOne() throws Exception {
        AiService ai = mock(AiService.class);
        var invented = (ObjectNode) mapper.readTree("{\"source\":\"groq\",\"markdown\":\"## Tóm tắt\\nBãi WR1 đạt 140%.\\n## Điểm nghẽn\\nx\\n## Khung cao điểm\\ny\\n## Khuyến nghị cho tuần tới\\nz\"}");
        when(ai.run(anyString(), anyString(), any(), any())).thenReturn(invented);
        assertEquals("mock", report(false, ai).report(WEEK).path("source").asString());
    }

    @Test void aGoodModelReportIsUsedWhenItHasAllSectionsAndNoInventedPercentage() throws Exception {
        AiService ai = mock(AiService.class);
        var good = (ObjectNode) mapper.readTree("{\"source\":\"groq\",\"markdown\":\"## Tóm tắt\\n8 lượt vào.\\n## Điểm nghẽn\\nWR1.\\n## Khung cao điểm\\n07:00.\\n## Khuyến nghị cho tuần tới\\nMở cổng sớm.\"}");
        when(ai.run(anyString(), anyString(), any(), any())).thenReturn(good);
        assertEquals("groq", report(false, ai).report(WEEK).path("source").asString());
    }

    @Test void aModelReportMissingASectionIsNotUsed() throws Exception {
        AiService ai = mock(AiService.class);
        var partial = (ObjectNode) mapper.readTree("{\"source\":\"groq\",\"markdown\":\"## Tóm tắt\\nchỉ có một mục.\"}");
        when(ai.run(anyString(), anyString(), any(), any())).thenReturn(partial);
        assertEquals("mock", report(false, ai).report(WEEK).path("source").asString());
    }
}
