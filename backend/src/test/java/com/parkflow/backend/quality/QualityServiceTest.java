package com.parkflow.backend.quality;

import com.parkflow.backend.common.ParkFlowClock;
import com.parkflow.backend.common.SimulationTimeSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;

import static com.parkflow.backend.quality.QualityService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * H13 – Kịch bản đột biến dữ liệu trên giả lập.
 *
 * <p>Mỗi test mô phỏng một trạng thái bãi bất thường do đột biến (spike) tạo ra:
 * <ol>
 *   <li>Bãi im lặng trong giờ hoạt động → {@code signal_lost}</li>
 *   <li>raw_count âm sâu sau khi đột biến chỉ gửi lượt vào → {@code missing_out}</li>
 *   <li>Số xe vượt max_capacity do đột biến gửi quá nhiều lượt vào → {@code implausible_count}</li>
 *   <li>Kết hợp signal_lost + missing_out → hai cờ cùng lúc; primaryFlag = signal_lost</li>
 *   <li>Bãi bình thường → chỉ có cờ {@code ok}</li>
 * </ol>
 */
class QualityServiceTest {

    private JdbcTemplate jdbc;
    private ParkFlowClock clock;

    private QualityService service;

    // Giờ hiện tại cố định trong khung hoạt động (08:00 ICT)
    private static final OffsetDateTime NOW_OPERATING = OffsetDateTime.parse("2026-09-22T08:00:00+07:00");
    // Giờ hiện tại ngoài khung hoạt động (03:00 ICT)
    private static final OffsetDateTime NOW_IDLE = OffsetDateTime.parse("2026-09-22T03:00:00+07:00");

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        clock = mock(ParkFlowClock.class);
        service = new QualityService(jdbc, clock);
    }

    /** Dựng hàng bãi giả với raw_count và last_event_at tuỳ chỉnh. */
    private static Map<String, Object> row(String lotId, int rawCount, int currentCount,
                                           int maxCapacity, Instant lastEventAt) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("lot_id", lotId);
        m.put("raw_count", rawCount);
        m.put("current_count", currentCount);
        m.put("max_capacity", maxCapacity);
        m.put("last_event_at", lastEventAt == null ? null : Timestamp.from(lastEventAt));
        return m;
    }

    private void givenNow(OffsetDateTime now) {
        when(clock.read()).thenReturn(new ParkFlowClock.Reading("sim", now));
    }

    private void givenRows(List<Map<String, Object>> rows) {
        when(jdbc.queryForList(anyString())).thenReturn(rows);
    }

    // ── Kịch bản 1: signal_lost ──────────────────────────────────────────────

    @Test
    void signalLost_whenNoEventForMoreThanThreshold() {
        givenNow(NOW_OPERATING);
        // Bãi im hơn SIGNAL_LOST_MINUTES phút: last_event_at cách NOW 45 phút.
        Instant lastEventAt = NOW_OPERATING.toInstant().minusSeconds((SIGNAL_LOST_MINUTES + 15) * 60L);
        givenRows(List.of(row("A1", 0, 0, 200, lastEventAt)));

        var quality = service.flagsByLot();
        var flags = quality.get("A1").flags();

        assertTrue(flags.contains(QualityFlag.signal_lost), "Phải phát hiện signal_lost sau 45 phút im lặng");
        assertFalse(flags.contains(QualityFlag.missing_out));
        assertFalse(flags.contains(QualityFlag.implausible_count));
        assertEquals("signal_lost", quality.get("A1").primaryFlag());
    }

    @Test
    void noSignalLost_whenRecentEvent() {
        givenNow(NOW_OPERATING);
        // Có sự kiện 5 phút trước: không trễ hơn ngưỡng.
        Instant lastEventAt = NOW_OPERATING.toInstant().minusSeconds(5 * 60L);
        givenRows(List.of(row("A1", 0, 0, 200, lastEventAt)));

        var quality = service.flagsByLot();
        assertFalse(quality.get("A1").flags().contains(QualityFlag.signal_lost));
    }

    @Test
    void noSignalLost_outsideOperatingHours() {
        givenNow(NOW_IDLE); // 03:00 – ngoài khung 06:00–22:00
        // Dù không có sự kiện nào cũng không cờ
        givenRows(List.of(row("A1", 0, 0, 200, null)));

        var quality = service.flagsByLot();
        assertFalse(quality.get("A1").flags().contains(QualityFlag.signal_lost),
                "Ngoài giờ hoạt động không cờ signal_lost dù bãi im");
    }

    // ── Kịch bản 2: missing_out ──────────────────────────────────────────────

    @Test
    void missingOut_whenRawCountDeepNegative() {
        givenNow(NOW_OPERATING);
        // raw_count = -10 vượt ngưỡng -5
        Instant recent = NOW_OPERATING.toInstant().minusSeconds(60);
        givenRows(List.of(row("B2", -10, 0, 300, recent)));

        var quality = service.flagsByLot();
        assertTrue(quality.get("B2").flags().contains(QualityFlag.missing_out));
        assertEquals(Map.of("rawCount", -10, "threshold", MISSING_OUT_THRESHOLD),
                quality.get("B2").details().get("missing_out"));
    }

    @Test
    void noMissingOut_whenRawCountAtOrAboveThreshold() {
        givenNow(NOW_OPERATING);
        // raw_count = -4 (vừa trên ngưỡng -5)
        Instant recent = NOW_OPERATING.toInstant().minusSeconds(60);
        givenRows(List.of(row("C3", -4, 0, 300, recent)));

        var quality = service.flagsByLot();
        assertFalse(quality.get("C3").flags().contains(QualityFlag.missing_out));
    }

    // ── Kịch bản 3: implausible_count ───────────────────────────────────────

    @Test
    void implausibleCount_whenCurrentCountAtOrAboveMaxCapacity() {
        givenNow(NOW_OPERATING);
        // Đột biến gửi quá nhiều lượt vào: current_count đạt max_capacity
        Instant recent = NOW_OPERATING.toInstant().minusSeconds(60);
        givenRows(List.of(row("D4", 200, 200, 200, recent)));

        var quality = service.flagsByLot();
        assertTrue(quality.get("D4").flags().contains(QualityFlag.implausible_count));
    }

    @Test
    void noImplausible_whenCurrentCountBelowMaxCapacity() {
        givenNow(NOW_OPERATING);
        Instant recent = NOW_OPERATING.toInstant().minusSeconds(60);
        givenRows(List.of(row("D4", 150, 150, 200, recent)));

        var quality = service.flagsByLot();
        assertFalse(quality.get("D4").flags().contains(QualityFlag.implausible_count));
    }

    // ── Kịch bản 4: nhiều cờ – primaryFlag theo thứ tự ưu tiên ────────────

    @Test
    void combinedFlags_signalLostAndMissingOut_primaryIsSignalLost() {
        givenNow(NOW_OPERATING);
        // Bãi vừa im lặng vừa có raw_count âm sâu
        Instant stale = NOW_OPERATING.toInstant().minusSeconds((SIGNAL_LOST_MINUTES + 20) * 60L);
        givenRows(List.of(row("E5", -20, 0, 250, stale)));

        var quality = service.flagsByLot();
        var flags = quality.get("E5").flags();
        assertTrue(flags.contains(QualityFlag.signal_lost));
        assertTrue(flags.contains(QualityFlag.missing_out));
        assertEquals("signal_lost", quality.get("E5").primaryFlag(),
                "signal_lost phải có độ ưu tiên cao hơn missing_out");
    }

    // ── Kịch bản 5: bãi bình thường ────────────────────────────────────────

    @Test
    void ok_whenNormalConditions() {
        givenNow(NOW_OPERATING);
        Instant recent = NOW_OPERATING.toInstant().minusSeconds(120);
        givenRows(List.of(row("F6", 0, 50, 200, recent)));

        var quality = service.flagsByLot();
        var q = quality.get("F6");
        assertTrue(q.isOk());
        assertEquals("ok", q.primaryFlag());
        assertEquals(List.of(QualityFlag.ok), q.flags());
    }

    // ── Kịch bản 6: signal_lost khi không có last_event_at (bãi chưa từng nhận sự kiện) ──

    @Test
    void signalLost_whenLastEventAtIsNull_duringOperatingHours() {
        givenNow(NOW_OPERATING);
        givenRows(List.of(row("G7", 0, 0, 100, null)));

        var quality = service.flagsByLot();
        assertTrue(quality.get("G7").flags().contains(QualityFlag.signal_lost),
                "Bãi chưa từng có sự kiện trong giờ hoạt động → signal_lost");
    }

    // ── Kịch bản 7: nhiều bãi cùng lúc ─────────────────────────────────────

    @Test
    void multipleLots_eachFlaggedCorrectly() {
        givenNow(NOW_OPERATING);
        Instant recent = NOW_OPERATING.toInstant().minusSeconds(60);
        Instant stale = NOW_OPERATING.toInstant().minusSeconds((SIGNAL_LOST_MINUTES + 10) * 60L);
        givenRows(List.of(
                row("OK1",  0,  50, 200, recent),  // ok
                row("SL2",  0,   0, 200, stale),   // signal_lost
                row("MO3", -8,   0, 200, recent),  // missing_out
                row("IC4",  0, 200, 200, recent)   // implausible_count
        ));

        var quality = service.flagsByLot();
        assertEquals("ok",                 quality.get("OK1").primaryFlag());
        assertEquals("signal_lost",        quality.get("SL2").primaryFlag());
        assertEquals("missing_out",        quality.get("MO3").primaryFlag());
        assertEquals("implausible_count",  quality.get("IC4").primaryFlag());
    }
}
