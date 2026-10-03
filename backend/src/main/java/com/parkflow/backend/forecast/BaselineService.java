package com.parkflow.backend.forecast;

import com.parkflow.backend.common.ParkFlowClock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BaselineService {
    private final JdbcTemplate jdbc;

    public BaselineService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Point(LocalTime t, double count) {}
    private record Sample(int slot, long count) {}

    /**
     * Trung vị số xe của tối đa bốn ngày có lịch sử cùng thứ trước ngày mục tiêu.
     * Không có lịch sử trả danh sách rỗng; không biến ngày thiếu dữ liệu thành ngày 0 xe.
     */
    public List<Point> baseline(String lotId, LocalDate target) {
        if (lotId == null || lotId.isBlank()) throw new IllegalArgumentException("Lot is required");
        Objects.requireNonNull(target, "Target date is required");
        // Chỉ đưa tối đa 4 * 29 số tổng hợp về Java, không tải dữ liệu từng xe.
        List<Sample> samples = jdbc.query("""
                WITH days AS (
                    SELECT DISTINCT (ts AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day
                    FROM events
                    WHERE lot_id = ? AND historical = true AND ts < ?
                      AND EXTRACT(ISODOW FROM ts AT TIME ZONE 'Asia/Ho_Chi_Minh') = ?
                    ORDER BY day DESC LIMIT 4
                ), openings AS (
                    SELECT day, (day::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh') AS midnight,
                           (SELECT COUNT(*) FROM (
                               SELECT DISTINCT ON (card_hash) direction
                               FROM events
                               WHERE lot_id = ? AND historical = true
                                 AND ts < (day::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh')
                                 AND ts > (day::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh') - INTERVAL '24 hours'
                               ORDER BY card_hash, ts DESC, event_id DESC
                           ) latest WHERE direction = 'in') AS opening
                    FROM days
                )
                SELECT o.day, s.slot, o.opening + COALESCE(SUM(
                           CASE e.direction WHEN 'in' THEN 1 WHEN 'out' THEN -1 ELSE 0 END
                       ), 0) AS count
                FROM openings o CROSS JOIN generate_series(0, 28) AS s(slot)
                LEFT JOIN events e ON e.lot_id = ? AND e.historical = true
                    AND e.ts >= o.midnight
                    AND e.ts <= o.midnight + INTERVAL '6 hours' + s.slot * INTERVAL '15 minutes'
                GROUP BY o.day, s.slot, o.opening
                ORDER BY s.slot, o.day
                """, (rs, row) -> new Sample(rs.getInt("slot"), rs.getLong("count")),
                lotId, target.atStartOfDay(ParkFlowClock.ZONE).toOffsetDateTime(),
                target.getDayOfWeek().getValue(), lotId, lotId);
        if (samples.isEmpty()) return List.of();
        List<Point> result = new ArrayList<>(ForecastMath.SLOTS);
        for (int slot = 0; slot < ForecastMath.SLOTS; slot++) {
            final int index = slot;
            double[] counts = samples.stream().filter(s -> s.slot() == index)
                    .mapToDouble(Sample::count).toArray();
            result.add(new Point(ForecastMath.START.plusMinutes((long) slot * ForecastMath.SLOT_MINUTES),
                    Math.max(0, ForecastMath.median(counts))));
        }
        return List.copyOf(result);
    }
}
