package com.parkflow.backend.campusconfig;

import com.parkflow.backend.common.ApiException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class CampusConfigService {
    private static final List<String> THRESHOLD_KEYS = List.of("yellow", "warn", "over", "target", "targetMax", "ceiling");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /** Nạp lần đầu trong một giao dịch; không ghi đè cấu hình hoặc số xe đã có. */
    @Transactional
    public boolean seedIfAbsent(JsonNode body) {
        validate(body);
        jdbc.execute("SELECT pg_advisory_xact_lock(20261002)");
        if (!jdbc.queryForList("SELECT id FROM campus_config WHERE id = 1", Integer.class).isEmpty()) return false;
        for (JsonNode gate : body.path("gates")) {
            jdbc.update("""
                    INSERT INTO forecast_gates(gate,name,capacity_per_15,normal_open) VALUES (?,?,?,?)
                    ON CONFLICT(gate) DO NOTHING
                    """, gate.path("id").asText(), gate.path("name").asText(), gate.path("capacityPer15").asInt(), gate.path("normalOpen").asText());
        }
        for (JsonNode lot : body.path("lots")) {
            jdbc.update("""
                    INSERT INTO lots(lot_id,name,capacity,max_capacity,gate,near_building) VALUES (?,?,?,?,?,?)
                    ON CONFLICT(lot_id) DO NOTHING
                    """, lot.path("id").asText(), lot.path("name").asText(), lot.path("capacity").asInt(),
                    lot.path("max").asInt(), lot.path("gate").asText(), lot.path("near").asText(null));
        }
        for (JsonNode lot : body.path("lots")) {
            for (var share : lot.path("gateShare").properties())
                jdbc.update("INSERT INTO forecast_gate_shares(lot_id,gate,share) VALUES (?,?,?) ON CONFLICT(lot_id,gate) DO NOTHING",
                        lot.path("id").asText(), share.getKey(), share.getValue().asDouble());
            int rank = 1;
            for (JsonNode alternate : lot.path("alternates"))
                jdbc.update("INSERT INTO lot_alternates(lot_id,alternate_id,rank) VALUES (?,?,?) ON CONFLICT(lot_id,alternate_id) DO NOTHING",
                        lot.path("id").asText(), alternate.asText(), rank++);
        }
        jdbc.update("INSERT INTO campus_config(id,body) VALUES (1,?::jsonb)", mapper.writeValueAsString(body));
        return true;
    }

    @Transactional(readOnly = true)
    public ObjectNode current() {
        var stored = jdbc.queryForList("SELECT body::text FROM campus_config WHERE id=1", String.class);
        if (stored.isEmpty()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_NOT_READY", "Chưa nạp cấu hình trường.");
        return (ObjectNode) mapper.readTree(stored.get(0));
    }

    @Transactional(readOnly = true)
    public ObjectNode currentWithCounts() {
        var result = current();
        Map<String,Integer> counts = new HashMap<>();
        jdbc.query("SELECT lot_id,current_count FROM lots", (RowCallbackHandler) rs -> counts.put(rs.getString(1),rs.getInt(2)));
        for (JsonNode lot : result.path("lots")) ((ObjectNode)lot).put("count",counts.getOrDefault(lot.path("id").asText(),0));
        return result;
    }

    private record Parsed(Set<String> lotIds, Set<String> gateIds) {}

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONFIG", message);
    }

    private Parsed validate(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid("Cấu hình phải là một đối tượng JSON");
        if (body.path("school").asText("").isBlank()) throw invalid("Thiếu tên trường (school)");
        JsonNode thresholds = body.path("thresholds");
        for (String key : THRESHOLD_KEYS)
            if (!thresholds.path(key).isNumber()) throw invalid("Thiếu ngưỡng thresholds." + key);
        double yellow = thresholds.path("yellow").asDouble(), warn = thresholds.path("warn").asDouble();
        double over = thresholds.path("over").asDouble(), target = thresholds.path("target").asDouble();
        double targetMax = thresholds.path("targetMax").asDouble(), ceiling = thresholds.path("ceiling").asDouble();
        if (!(0 < yellow && yellow <= warn && warn <= over && 0 < target && target <= targetMax && over <= ceiling))
            throw invalid("Ngưỡng không hợp lệ: cần yellow ≤ warn ≤ over ≤ ceiling và target ≤ targetMax");

        JsonNode gates = body.path("gates");
        if (!gates.isArray() || gates.isEmpty()) throw invalid("Cần ít nhất một cổng (gates)");
        Set<String> gateIds = new LinkedHashSet<>();
        for (JsonNode gate : gates) {
            String id = gate.path("id").asText("");
            if (id.isBlank() || id.length() > 20) throw invalid("Mã cổng không hợp lệ");
            if (!gateIds.add(id)) throw invalid("Trùng mã cổng " + id);
            if (gate.path("name").asText("").isBlank()) throw invalid("Cổng " + id + " thiếu tên");
            if (!gate.path("capacityPer15").isIntegralNumber() || gate.path("capacityPer15").asInt() <= 0)
                throw invalid("Cổng " + id + ": capacityPer15 phải là số nguyên dương");
            try { LocalTime.parse(gate.path("normalOpen").asText("")); }
            catch (DateTimeParseException e) { throw invalid("Cổng " + id + ": normalOpen phải có dạng HH:mm"); }
        }

        JsonNode lots = body.path("lots");
        if (!lots.isArray() || lots.isEmpty()) throw invalid("Cần ít nhất một bãi (lots)");
        Set<String> lotIds = new LinkedHashSet<>();
        for (JsonNode lot : lots) {
            String id = lot.path("id").asText("");
            if (id.isBlank() || id.length() > 20) throw invalid("Mã bãi không hợp lệ (tối đa 20 ký tự)");
            if (!lotIds.add(id)) throw invalid("Trùng mã bãi " + id);
        }
        for (JsonNode lot : lots) {
            String id = lot.path("id").asText();
            if (lot.path("name").asText("").isBlank()) throw invalid("Bãi " + id + " thiếu tên");
            if (!lot.path("capacity").isIntegralNumber() || !lot.path("max").isIntegralNumber()
                    || lot.path("capacity").asInt() <= 0 || lot.path("capacity").asInt() > lot.path("max").asInt())
                throw invalid("Bãi " + id + ": cần 0 < capacity ≤ max (số nguyên)");
            if (!gateIds.contains(lot.path("gate").asText())) throw invalid("Bãi " + id + ": cổng mặc định không tồn tại");
            double sum = 0;
            JsonNode shares = lot.path("gateShare");
            if (shares.isMissingNode() || !shares.isObject() || shares.isEmpty()) throw invalid("Bãi " + id + " thiếu gateShare");
            for (var share : shares.properties()) {
                if (!gateIds.contains(share.getKey())) throw invalid("Bãi " + id + ": gateShare trỏ tới cổng không tồn tại " + share.getKey());
                if (!share.getValue().isNumber() || share.getValue().asDouble() < 0 || share.getValue().asDouble() > 1)
                    throw invalid("Bãi " + id + ": gateShare phải nằm trong [0, 1]");
                sum += share.getValue().asDouble();
            }
            if (Math.abs(sum - 1.0) > 1e-6) throw invalid("Bãi " + id + ": tổng gateShare phải bằng 1");
            Set<String> seen = new HashSet<>();
            for (JsonNode alt : lot.path("alternates")) {
                String a = alt.asText("");
                if (!lotIds.contains(a)) throw invalid("Bãi " + id + ": bãi thay thế không tồn tại " + a);
                if (a.equals(id)) throw invalid("Bãi " + id + ": không được chọn chính nó làm bãi thay thế");
                if (!seen.add(a)) throw invalid("Bãi " + id + ": trùng bãi thay thế " + a);
            }
        }

        JsonNode buildings = body.path("buildings");
        if (!buildings.isArray()) throw invalid("Thiếu danh sách tòa nhà (buildings)");
        Set<String> buildingIds = new HashSet<>();
        for (JsonNode b : buildings) {
            String id = b.path("id").asText("");
            if (id.isBlank() || !buildingIds.add(id)) throw invalid("Mã tòa nhà trống hoặc trùng: " + id);
            if (!lotIds.contains(b.path("nearLot").asText())) throw invalid("Tòa " + id + ": nearLot không tồn tại");
        }

        JsonNode routes = body.path("routes");
        if (!routes.isArray()) throw invalid("Thiếu danh sách tuyến (routes)");
        Set<String> pairs = new HashSet<>();
        for (JsonNode r : routes) {
            String gate = r.path("gate").asText(""), lot = r.path("lot").asText("");
            if (!gateIds.contains(gate) || !lotIds.contains(lot)) throw invalid("Tuyến " + gate + "→" + lot + " trỏ tới cổng hoặc bãi không tồn tại");
            if (!pairs.add(gate + "→" + lot)) throw invalid("Trùng tuyến " + gate + "→" + lot);
        }
        if (pairs.size() != gateIds.size() * lotIds.size())
            throw invalid("Cần đủ " + gateIds.size() * lotIds.size() + " tuyến (mỗi cặp cổng × bãi), hiện có " + pairs.size());
        return new Parsed(lotIds, gateIds);
    }

}
