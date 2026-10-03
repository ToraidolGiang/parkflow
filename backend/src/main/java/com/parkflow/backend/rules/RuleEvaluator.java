package com.parkflow.backend.rules;

import com.parkflow.backend.ai.ForecastPeakSource;
import com.parkflow.backend.common.ParkFlowClock;
import com.parkflow.backend.ingest.LotStatusService;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Mỗi phút đánh giá các quy tắc cảnh báo đang bật: trong khung `from`–`to` của quy tắc, bãi nào thỏa `metric op value` thì tạo cảnh báo
 * (mỗi quy tắc, mỗi bãi một lần mỗi ngày). Dùng đồng hồ chung nên theo giờ giả lập khi đang chạy. Quy tắc điều hướng (`routing`) không do đây xử lý.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleEvaluator {
    private final RuleStore rules;
    private final AlertService alerts;
    private final LotStatusService lotStatus;
    private final ObjectProvider<ForecastPeakSource> forecast;
    private final JdbcTemplate jdbc;
    private final ParkFlowClock clock;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void evaluate() {
        try {
            evaluate(clock.read().now());
        } catch (RuntimeException e) {
            log.warn("Không đánh giá được quy tắc: {}", e.getMessage());
        }
    }

    /** Trả số cảnh báo mới tạo. */
    public int evaluate(OffsetDateTime now) {
        List<RuleStore.Rule> active = rules.list().stream().filter(r -> r.enabled() && "alert".equals(r.type())).toList();
        if (active.isEmpty()) return 0;
        LocalTime local = now.atZoneSameInstant(ParkFlowClock.ZONE).toLocalTime();
        Map<String, Double> current = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Object item : (Iterable<?>) lotStatus.status().get("lots")) {
            @SuppressWarnings("unchecked") Map<String, Object> lot = (Map<String, Object>) item;
            current.put(lot.get("lotId").toString(), ((Number) lot.get("pct")).doubleValue());
            names.put(lot.get("lotId").toString(), lot.get("lotId").toString());
        }
        Map<String, ForecastPeakSource.Peak> peaks = new HashMap<>();
        ForecastPeakSource source = forecast.getIfAvailable();
        if (source != null) source.peaks().forEach(p -> peaks.put(p.lotId(), p));
        Map<String, Integer> inflow = null;

        int created = 0;
        for (RuleStore.Rule rule : active) {
            if (local.isBefore(LocalTime.parse(rule.from())) || local.isAfter(LocalTime.parse(rule.to()))) continue;
            if ("inflow_15m".equals(rule.metric()) && inflow == null) inflow = inflowLast15(now);
            for (String lotId : names.keySet()) {
                if (!"ALL".equals(rule.scope()) && !rule.scope().equals(lotId)) continue;
                String message = null;
                switch (rule.metric()) {
                    case "current_pct" -> {
                        Double pct = current.get(lotId);
                        if (pct != null && compare(pct, rule.op(), rule.value()))
                            message = "Bãi " + lotId + " đang " + format(pct) + "% (" + rule.op() + " " + format(rule.value()) + "%)";
                    }
                    case "forecast_pct" -> {
                        ForecastPeakSource.Peak peak = peaks.get(lotId);
                        if (peak != null && compare(peak.pct(), rule.op(), rule.value()))
                            message = "Bãi " + lotId + " dự báo " + format(peak.pct()) + "% lúc " + peak.time() + " (" + rule.op() + " " + format(rule.value()) + "%)";
                    }
                    default -> {
                        int count = inflow.getOrDefault(lotId, 0);
                        if (compare(count, rule.op(), rule.value()))
                            message = "Bãi " + lotId + " có " + count + " xe vào trong 15 phút (" + rule.op() + " " + format(rule.value()) + ")";
                    }
                }
                if (message != null && alerts.fire(rule.id(), lotId, message, now)) created++;
            }
        }
        return created;
    }

    private Map<String, Integer> inflowLast15(OffsetDateTime now) {
        Map<String, Integer> result = new HashMap<>();
        jdbc.query("SELECT lot_id, COUNT(*) AS c FROM events WHERE direction = 'in' AND ts > ? AND ts <= ? GROUP BY lot_id",
                rs -> { result.put(rs.getString("lot_id"), rs.getInt("c")); }, now.minusMinutes(15), now);
        return result;
    }

    static boolean compare(double actual, String op, double threshold) {
        return switch (op) {
            case ">=" -> actual >= threshold;
            case ">" -> actual > threshold;
            case "<=" -> actual <= threshold;
            case "<" -> actual < threshold;
            default -> false;
        };
    }

    private static String format(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded).replace('.', ',');
    }
}
