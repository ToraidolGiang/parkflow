package com.parkflow.backend.forecast;

import com.parkflow.backend.common.ApiException;
import com.parkflow.backend.common.ParkFlowClock;
import com.parkflow.backend.repository.LotRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ForecastService {
    private final BaselineService baseline;
    private final LotRepository lots;
    private final ParkFlowClock clock;
    private final JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired
    private com.parkflow.backend.approval.PlanVersionService plans;

    public ForecastService(BaselineService baseline, LotRepository lots, ParkFlowClock clock, JdbcTemplate jdbc) {
        this.baseline = baseline;
        this.lots = lots;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    public record Point(LocalTime t, double count) {}
    public record Forecast(LocalDate date, OffsetDateTime generatedAt, double k, List<Point> points) {}

    public Forecast forecast(String lotId, LocalDate date) {
        if (lotId == null || lotId.isBlank() || date == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Cần mã bãi và ngày dự báo");
        }
        var lot = lots.findById(lotId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "UNKNOWN_LOT", "Không tìm thấy bãi"));
        var now = clock.read().now();
        var base = baseline.baseline(lotId, date);
        if (base.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NO_HISTORY", "Chưa có lịch sử cùng thứ để dự báo");
        }
        boolean inWindow = date.equals(now.toLocalDate())
                && !now.toLocalTime().isBefore(ForecastMath.START)
                && !now.toLocalTime().isAfter(ForecastMath.END);
        if (!inWindow) {
            return new Forecast(date, now, 1, base.stream().map(p -> new Point(p.t(), p.count())).toList());
        }
        double baseNow = ForecastMath.interpolate(base.stream().mapToDouble(BaselineService.Point::count).toArray(), now.toLocalTime());
        // Sự kiện đúng 06:00 đã nằm trong số đầu ca; chỉ cộng các sự kiện sau mốc đó.
        Long netActual = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE direction WHEN 'in' THEN 1 ELSE -1 END), 0)
                FROM events WHERE lot_id = ? AND ts > ? AND ts <= ? AND historical = false
                """, Long.class, lotId, date.atTime(ForecastMath.START)
                        .atZone(ParkFlowClock.ZONE).toOffsetDateTime(), now);
        double k = ForecastMath.factor(netActual == null ? 0 : netActual, baseNow - base.get(0).count());
        int current = lot.getCurrentCount();
        return new Forecast(date, now, k, base.stream().map(p -> new Point(p.t(),
                p.t().isBefore(now.toLocalTime()) ? p.count()
                        : ForecastMath.corrected(current, k, p.count(), baseNow))).toList());
    }
    public ForecastResponse getForecast(String lotId, LocalDate date) {
        Forecast calculated = forecast(lotId, date);
        var lot = lots.findById(lotId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "UNKNOWN_LOT", "Không tìm thấy bãi"));
        var midnight = date.atStartOfDay(ParkFlowClock.ZONE).toOffsetDateTime();
        var actuals = jdbc.query("""
                WITH opening AS (
                    SELECT COUNT(*) AS count FROM (
                        SELECT DISTINCT ON (card_hash) direction FROM events
                        WHERE lot_id = ? AND ts < ? AND ts > ?
                        ORDER BY card_hash, ts DESC, event_id DESC
                    ) latest WHERE direction = 'in'
                )
                SELECT s.slot, o.count + COALESCE(SUM(
                    CASE e.direction WHEN 'in' THEN 1 WHEN 'out' THEN -1 ELSE 0 END), 0) AS count
                FROM opening o CROSS JOIN generate_series(0,28) s(slot)
                LEFT JOIN events e ON e.lot_id = ? AND e.ts >= ?
                    AND e.ts <= ?::timestamptz + INTERVAL '13 hours'
                    AND e.ts <= ?::timestamptz + INTERVAL '6 hours' + s.slot * INTERVAL '15 minutes'
                WHERE ?::timestamptz + INTERVAL '6 hours' + s.slot * INTERVAL '15 minutes' <= ?
                GROUP BY s.slot, o.count ORDER BY s.slot
                """, (rs, row) -> new Actual(rs.getInt("slot"), Math.max(0, rs.getInt("count"))),
                lotId, midnight, midnight.minusHours(24), lotId, midnight, midnight, midnight,
                midnight, calculated.generatedAt());
        java.util.Map<Integer, Integer> bySlot = new java.util.HashMap<>();
        actuals.forEach(a -> bySlot.put(a.slot(), a.count()));
        var points = new java.util.ArrayList<ForecastResponse.Point>();
        for (int i = 0; i < calculated.points().size(); i++) {
            var p = calculated.points().get(i);
            int count = (int) Math.round(p.count());
            // Chỉ áp dụng kế hoạch đã duyệt, trong đúng cửa sổ điều hướng.
            points.add(new ForecastResponse.Point(p.t().toString(), bySlot.get(i), count,
                    plans.afterPlan(date, lotId, p.t(), count)));
        }
        var peak = points.get(0);
        for (var p : points) if (p.forecast() > peak.forecast()) peak = p;
        return new ForecastResponse(lotId, date, Math.round(calculated.k() * 1000) / 1000.0,
                calculated.generatedAt(), new ForecastResponse.Peak(peak.t(), peak.forecast(),
                Math.round(peak.forecast() * 1000.0 / lot.getCapacity()) / 10.0),
                List.copyOf(points), new ForecastResponse.Thresholds(80, 100, 120));
    }

    private record Actual(int slot, int count) {}
}