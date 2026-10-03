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
}