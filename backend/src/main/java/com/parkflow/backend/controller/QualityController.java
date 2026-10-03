package com.parkflow.backend.controller;

import com.parkflow.backend.quality.LotQuality;
import com.parkflow.backend.quality.QualityService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/quality/flags} – trả cờ chất lượng dữ liệu cho tất cả bãi.
 *
 * <p>Phản hồi mẫu:
 * <pre>
 * {
 *   "lots": [
 *     {
 *       "lotId": "A1",
 *       "flags": ["signal_lost"],
 *       "primaryFlag": "signal_lost",
 *       "details": { "signal_lost": { "lastEventAt": null, "silentMinutes": null, "thresholdMinutes": 30 } }
 *     },
 *     { "lotId": "B2", "flags": ["ok"], "primaryFlag": "ok", "details": {} }
 *   ]
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityService qualityService;

    @GetMapping("/flags")
    public Map<String, Object> flags() {
        Map<String, LotQuality> byLot = qualityService.flagsByLot();
        List<Map<String, Object>> lots = new ArrayList<>(byLot.size());
        for (LotQuality q : byLot.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("lotId", q.lotId());
            item.put("flags", q.flags().stream().map(Enum::name).toList());
            item.put("primaryFlag", q.primaryFlag());
            item.put("details", q.details());
            lots.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lots", lots);
        return result;
    }
}
