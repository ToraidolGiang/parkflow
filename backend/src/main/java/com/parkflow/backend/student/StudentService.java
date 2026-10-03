package com.parkflow.backend.student;

import com.parkflow.backend.campusconfig.CampusConfigService;
import com.parkflow.backend.common.ParkFlowClock;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Gợi ý bãi gần tòa và tuyến tới cổng theo cấu hình campus đã seed. */
@Service
@RequiredArgsConstructor
public class StudentService {
    private final CampusConfigService config;
    private final JdbcTemplate jdbc;
    private final ParkFlowClock clock;
    private final ObjectMapper mapper;

    public ObjectNode recommend(String building, String arrive, String sessionId) {
        if (building == null || building.isBlank() || sessionId == null || sessionId.isBlank())
            throw new IllegalArgumentException("building và sessionId là bắt buộc");
        ObjectNode campus = config.currentWithCounts();
        JsonNode destination = campus.path("buildings").findValue(building);
        if (destination == null || destination.isMissingNode()) throw new IllegalArgumentException("Unknown building");
        String lotId = destination.path("nearLot").asText();
        JsonNode lot = find(campus.path("lots"), lotId);
        String gateId = lot.path("gate").asText();
        JsonNode gate = find(campus.path("gates"), gateId);
        JsonNode route = findRoute(campus.path("routes"), gateId, lotId);
        ObjectNode result = mapper.createObjectNode();
        result.put("lotId", lotId).put("lotName", lot.path("name").asText()).put("gate", gateId)
                .put("gateName", gate.path("name").asText()).put("openFrom", gate.path("normalOpen").asText("06:00"))
                .put("expectedPct", lot.path("capacity").asDouble() == 0 ? 0 : lot.path("count").asDouble() * 100 / lot.path("capacity").asDouble())
                .put("reason", "Gợi ý bãi gần tòa học theo cấu hình trường.")
                .put("changed", false).put("updatedAt", clock.read().now().toString()).putNull("planVersion").putNull("changeMessage");
        ObjectNode routeResult = result.putObject("route");
        routeResult.put("mapsUrl", gate.path("lat").isNumber() && gate.path("lng").isNumber()
                ? "https://www.google.com/maps/dir/?api=1&destination=" + gate.path("lat").asDouble() + "," + gate.path("lng").asDouble() : "");
        routeResult.set("path", route.path("path"));
        routeResult.set("steps", route.path("steps"));
        routeResult.put("walkMinutes", route.path("walkMinutes").path(building).asInt(0));
        return result;
    }

    private static JsonNode find(JsonNode values, String id) {
        for (JsonNode value : values) if (id.equals(value.path("id").asText())) return value;
        throw new IllegalArgumentException("Unknown campus item: " + id);
    }
    private JsonNode findRoute(JsonNode values, String gate, String lot) {
        for (JsonNode value : values) if (gate.equals(value.path("gate").asText()) && lot.equals(value.path("lot").asText())) return value;
        return mapper.createObjectNode();
    }
}
