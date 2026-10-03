package com.parkflow.backend.ingest;

import com.parkflow.backend.approval.PlanVersionService;
import com.parkflow.backend.common.ApiException;
import com.parkflow.backend.common.ParkFlowClock;
import com.parkflow.backend.forecast.ForecastResponse;
import com.parkflow.backend.forecast.ForecastService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Tình hình từng cổng cho màn Bảo vệ (docs/thiet-ke/openapi.yaml `/gates/status`).
 * Chưa có đầu đếm ở cổng nên `source` luôn là `estimated`: xe vào cổng = xe vào từng bãi trong 15 phút gần nhất nhân tỷ lệ cổng
 * (`forecast_gate_shares` từ cấu hình trường). `expectedIn` là mức tăng dự báo của từng khung 15 phút (phần dương) nhân tỷ lệ cổng,
 * từ chính dự báo của trang Dự báo; bãi chưa có lịch sử thì không đóng góp. `action` lấy từ kế hoạch ĐANG ÁP DỤNG của hôm nay.
 */
@Service
@RequiredArgsConstructor
public class GateStatusService {
    private static final String FIRST_SLOT = "06:00";
    private static final String LAST_SLOT = "09:30";

    private final JdbcTemplate jdbc;
    private final ParkFlowClock clock;
    private final ForecastService forecast;
    private final PlanVersionService plans;

    public Map<String, Object> status() {
        ParkFlowClock.Reading reading = clock.read();
        OffsetDateTime now = reading.now();
        LocalDate today = now.toLocalDate();

        Map<String, Map<String, Double>> shares = new HashMap<>();   // bãi -> cổng -> tỷ lệ
        jdbc.query("SELECT lot_id, gate, share FROM forecast_gate_shares", rs -> {
            shares.computeIfAbsent(rs.getString("lot_id"), k -> new HashMap<>()).put(rs.getString("gate"), rs.getDouble("share"));
        });
        Map<String, Integer> lotInflow = new HashMap<>();
        jdbc.query("SELECT lot_id, COUNT(*) AS c FROM events WHERE direction = 'in' AND ts > ? AND ts <= ? GROUP BY lot_id",
                rs -> { lotInflow.put(rs.getString("lot_id"), rs.getInt("c")); }, now.minusMinutes(15), now);

        Map<String, Double> gateInflow = new HashMap<>();
        Map<String, TreeMap<String, Double>> gateExpected = new HashMap<>();
        for (var lot : shares.entrySet()) {
            int inflow = lotInflow.getOrDefault(lot.getKey(), 0);
            lot.getValue().forEach((gate, share) -> gateInflow.merge(gate, inflow * share, Double::sum));
            List<ForecastResponse.Point> points = forecastPoints(lot.getKey(), today);
            for (int i = 1; i < points.size(); i++) {
                String t = points.get(i).t();
                if (t.compareTo(FIRST_SLOT) < 0 || t.compareTo(LAST_SLOT) > 0) continue;
                double increase = Math.max(0, points.get(i).forecast() - points.get(i - 1).forecast());
                lot.getValue().forEach((gate, share) -> gateExpected.computeIfAbsent(gate, k -> new TreeMap<>()).merge(t, increase * share, Double::sum));
            }
        }

        Map<String, JsonNode> actions = new HashMap<>();
        plans.applied(today).ifPresent(version -> version.body().path("gateActions").forEach(a -> actions.put(a.path("gate").asString(), a)));

        List<Map<String, Object>> gates = new ArrayList<>();
        jdbc.query("SELECT gate, name, capacity_per_15 FROM forecast_gates ORDER BY gate", rs -> {
            String id = rs.getString("gate");
            int capacity = rs.getInt("capacity_per_15");
            int inflow = (int) Math.round(gateInflow.getOrDefault(id, 0.0));
            double utilization = capacity <= 0 ? 0 : Math.round(inflow * 100.0 / capacity) / 100.0;
            Map<String, Object> gate = new LinkedHashMap<>();
            gate.put("gate", id);
            gate.put("name", rs.getString("name"));
            gate.put("source", "estimated");
            gate.put("capacityPer15", capacity);
            gate.put("inflowLast15", inflow);
            gate.put("utilization", utilization);
            gate.put("level", LotStatusService.level(utilization * 100));
            List<Map<String, Object>> expected = new ArrayList<>();
            gateExpected.getOrDefault(id, new TreeMap<>()).forEach((t, count) -> {
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("t", t);
                slot.put("count", (int) Math.round(count));
                expected.add(slot);
            });
            gate.put("expectedIn", expected);
            JsonNode action = actions.get(id);
            if (action == null) gate.put("action", null);
            else {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("openFrom", action.path("openFrom").asString());
                a.put("normalOpen", action.path("normalOpen").asString());
                a.put("extraGuard", action.path("extraGuard").asString());
                gate.put("action", a);
            }
            gates.add(gate);
        });

        Map<String, Object> clockNode = new LinkedHashMap<>();
        clockNode.put("mode", reading.mode());
        clockNode.put("now", now.toString());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clock", clockNode);
        result.put("gates", gates);
        return result;
    }

    /** Điểm dự báo hôm nay của một bãi; chưa có lịch sử thì rỗng (không bịa số). */
    private List<ForecastResponse.Point> forecastPoints(String lotId, LocalDate date) {
        try {
            return forecast.getForecast(lotId, date).points();
        } catch (ApiException e) {
            return List.of();
        }
    }
}
