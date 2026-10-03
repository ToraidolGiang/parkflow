package com.parkflow.backend.ingest;

import com.parkflow.backend.approval.PlanVersionService;
import com.parkflow.backend.common.ApiException;
import com.parkflow.backend.common.ParkFlowClock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Tỷ lệ làm theo gợi ý của một phiên bản kế hoạch: `compliance = số xe thực tế vào bãi đích trong khung / số suất đã phát cho bãi đích`
 * (docs/thiet-ke/03 mục 7). Khung là `window` của kế hoạch trong ngày áp dụng; số suất là `quota` của từng lượt chuyển. Mỗi tỷ lệ được kẹp tối đa 1,0
 * (xe đến vượt số suất không làm tỷ lệ vượt 100%). Chưa có số đo (không có lượt chuyển hay chưa có suất) thì `overall` là 1,0.
 */
@Service
@RequiredArgsConstructor
public class ComplianceService {
    private final PlanVersionService plans;
    private final JdbcTemplate jdbc;

    public Map<String, Object> compliance(long version) {
        var found = plans.find(version).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Không có phiên bản kế hoạch này."));
        JsonNode body = found.body();
        LocalDate date = found.date();
        OffsetDateTime from = at(date, body.path("window").path("from").asString("00:00"));
        OffsetDateTime to = at(date, body.path("window").path("to").asString("23:59"));

        List<Map<String, Object>> byMove = new ArrayList<>();
        long issuedTotal = 0;
        double arrivedCapped = 0;
        for (JsonNode move : body.path("moves")) {
            int issued = move.path("quota").asInt(0);
            String target = move.path("to").asString();
            Integer arrived = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM events WHERE lot_id = ? AND direction = 'in' AND historical = false AND ts > ? AND ts <= ?
                    """, Integer.class, target, from, to);
            int arrivedCount = arrived == null ? 0 : arrived;
            double ratio = issued <= 0 ? 1.0 : Math.min(1.0, (double) arrivedCount / issued);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("from", move.path("from").asString());
            row.put("to", target);
            row.put("issued", issued);
            row.put("arrived", arrivedCount);
            row.put("compliance", round(ratio));
            byMove.add(row);
            if (issued > 0) {
                issuedTotal += issued;
                arrivedCapped += Math.min(issued, arrivedCount);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", found.version());
        result.put("overall", issuedTotal == 0 ? 1.0 : round(arrivedCapped / issuedTotal));
        result.put("byMove", byMove);
        return result;
    }

    private static OffsetDateTime at(LocalDate date, String hhmm) {
        return date.atTime(LocalTime.parse(hhmm)).atZone(ParkFlowClock.ZONE).toOffsetDateTime();
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
