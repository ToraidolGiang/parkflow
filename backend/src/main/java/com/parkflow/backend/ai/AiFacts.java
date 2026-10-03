package com.parkflow.backend.ai;

import com.parkflow.backend.ingest.LotStatusService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Gom số liệu tổng hợp đưa cho AI (docs/thiet-ke/08-ai-prompts.md mục 1): số do backend tính, AI chỉ diễn giải.
 * Chỉ có số xe, đỉnh dự báo và kế hoạch; **không bao giờ có mã thẻ hay dữ liệu từng xe**.
 */
@Component
@RequiredArgsConstructor
public class AiFacts {
    private final LotStatusService lotStatus;
    private final ObjectProvider<ForecastPeakSource> forecast;
    private final ObjectProvider<PlanSource> plan;
    private final ObjectMapper mapper;

    @SuppressWarnings("unchecked")
    public ObjectNode build() {
        Map<String, Object> status = lotStatus.status();
        ObjectNode facts = mapper.createObjectNode();
        facts.put("asOf", ((Map<String, Object>) status.get("clock")).get("now").toString());

        var lots = facts.putArray("lots");
        for (Object item : (Iterable<?>) status.get("lots")) {
            Map<String, Object> lot = (Map<String, Object>) item;
            lots.addObject().put("lotId", lot.get("lotId").toString()).put("name", lot.get("name").toString())
                    .put("count", ((Number) lot.get("count")).intValue()).put("capacity", ((Number) lot.get("capacity")).intValue())
                    .put("pct", ((Number) lot.get("pct")).doubleValue()).put("dataFlag", lot.get("dataFlag").toString());
        }

        var peaks = facts.putArray("forecastPeaks");
        ForecastPeakSource source = forecast.getIfAvailable();
        if (source != null) {
            for (ForecastPeakSource.Peak peak : source.peaks())
                peaks.addObject().put("lotId", peak.lotId()).put("time", peak.time()).put("count", peak.count()).put("pct", peak.pct());
        }
        // Không có đỉnh dự báo thì AI không được suy ra "không quá tải" từ số xe hiện tại.
        if (peaks.isEmpty()) facts.put("forecastNote", "Chưa có dự báo cho bãi nào; không kết luận về nguy cơ quá tải.");

        PlanSource planSource = plan.getIfAvailable();
        var current = planSource == null ? java.util.Optional.<ObjectNode>empty() : planSource.current();
        if (current.isPresent()) facts.set("plan", current.get()); else facts.putNull("plan");
        return facts;
    }
}
