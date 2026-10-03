package com.parkflow.backend.ingest;

import com.parkflow.backend.common.ParkFlowClock;
import com.parkflow.backend.quality.LotQuality;
import com.parkflow.backend.quality.QualityService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Trạng thái hiện tại của các bãi cho giao diện (hợp đồng: docs/thiet-ke/04-api.md mục 2).
 * `level` theo sức chứa THIẾT KẾ: green (dưới 80%), yellow (80 đến dưới 100%), red (100 đến dưới 120%), critical (từ 120%).
 * `dataFlag` lấy từ {@link QualityService#flagsByLot()} (H12).
 */
@Service
@RequiredArgsConstructor
public class LotStatusService {
    private final JdbcTemplate jdbc;
    private final ParkFlowClock clock;
    private final QualityService qualityService;

    static String level(double pct) {
        if (pct >= 120) return "critical";
        if (pct >= 100) return "red";
        if (pct >= 80) return "yellow";
        return "green";
    }

    static double pct(int count, int capacity) {
        return capacity <= 0 ? 0 : Math.round(count * 1000.0 / capacity) / 10.0;
    }

    public Map<String, Object> status() {
        ParkFlowClock.Reading reading = clock.read();
        OffsetDateTime now = reading.now();
        List<Map<String, Object>> lots = new ArrayList<>();
        int total = 0;
        int overloaded = 0;
        var rows = jdbc.queryForList("""
                SELECT lot_id, name, current_count, capacity, max_capacity, gate, near_building, last_event_at
                FROM lots ORDER BY lot_id
                """);
        // Lấy cờ chất lượng một lần cho toàn bộ bãi (H12).
        Map<String, LotQuality> qualityByLot = qualityService.flagsByLot();
        for (var row : rows) {
            int count = ((Number) row.get("current_count")).intValue();
            int capacity = ((Number) row.get("capacity")).intValue();
            double pct = pct(count, capacity);
            Map<String, Object> lot = new LinkedHashMap<>();
            lot.put("lotId", row.get("lot_id"));
            lot.put("name", row.get("name"));
            lot.put("count", count);
            lot.put("capacity", capacity);
            lot.put("maxCapacity", ((Number) row.get("max_capacity")).intValue());
            lot.put("pct", pct);
            lot.put("level", level(pct));
            lot.put("gate", row.get("gate"));
            lot.put("nearBuilding", row.get("near_building"));
            Object last = row.get("last_event_at");
            lot.put("lastEventAt", last == null ? null : ((java.sql.Timestamp) last).toInstant().atZone(ParkFlowClock.ZONE).toOffsetDateTime().toString());
            // dataFlag từ QualityService (H12); mặc định "ok" nếu bãi chưa có bản ghi chất lượng.
            String lotId = (String) row.get("lot_id");
            lot.put("dataFlag", qualityByLot.getOrDefault(lotId,
                    new LotQuality(lotId, java.util.List.of(com.parkflow.backend.quality.QualityFlag.ok), java.util.Map.of()))
                    .primaryFlag());
            lots.add(lot);
            total += count;
            if (pct >= 100) overloaded++;
        }
        Integer inflow = jdbc.queryForObject("""
                SELECT COUNT(*) FROM events WHERE direction = 'in' AND ts > ? AND ts <= ?
                """, Integer.class, now.minusMinutes(15), now);

        Map<String, Object> clockNode = new LinkedHashMap<>();
        clockNode.put("mode", reading.mode());
        clockNode.put("now", now.toString());
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("count", total);
        totals.put("overloadedLots", overloaded);
        totals.put("inflowLast15", inflow == null ? 0 : inflow);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clock", clockNode);
        result.put("updatedAt", now.toString());
        result.put("lots", lots);
        result.put("totals", totals);
        return result;
    }
}
