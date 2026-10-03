package com.parkflow.backend.controller;

import com.parkflow.backend.common.ApiKeyGuard;
import com.parkflow.backend.ingest.EventIngestService;
import com.parkflow.backend.ingest.EventValidator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** `POST /api/events`: nhận một sự kiện hoặc một mảng tối đa 500 (docs/thiet-ke/04-api.md mục 1). */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {
    static final int MAX_BATCH = 500;

    private final ApiKeyGuard apiKeyGuard;
    private final EventValidator validator;
    private final EventIngestService ingest;

    @PostMapping
    public ResponseEntity<Map<String, Object>> receive(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
                                                       @RequestBody JsonNode body) {
        apiKeyGuard.check(apiKey);
        List<JsonNode> items = new ArrayList<>();
        if (body.isArray()) body.forEach(items::add); else items.add(body);
        if (items.size() > MAX_BATCH) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "BATCH_TOO_LARGE");
            error.put("message", "Quá " + MAX_BATCH + " sự kiện trong một lần gửi.");
            return ResponseEntity.status(413).body(error);
        }

        int accepted = 0;
        int duplicate = 0;
        List<Map<String, Object>> rejected = new ArrayList<>();
        for (JsonNode item : items) {
            EventValidator.Result checked = validator.validate(item);
            if (!checked.isValid()) {
                rejected.add(rejection(checked.eventId(), checked.reason()));
                continue;
            }
            switch (ingest.ingest(checked.event())) {
                case ACCEPTED -> accepted++;
                case DUPLICATE -> duplicate++;
                case UNKNOWN_LOT -> rejected.add(rejection(checked.eventId(), "UNKNOWN_LOT"));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", accepted);
        result.put("duplicate", duplicate);
        result.put("rejected", rejected);
        return ResponseEntity.ok(result);
    }

    private static Map<String, Object> rejection(String eventId, String reason) {
        Map<String, Object> rejection = new LinkedHashMap<>();
        rejection.put("event_id", eventId);
        rejection.put("reason", reason);
        return rejection;
    }
}
