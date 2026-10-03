package com.parkflow.backend.ai;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Trợ lý dữ liệu mẫu: dựng câu trả lời hoàn toàn từ `facts` (số liệu do bộ tính tạo ra), không gọi mô hình.
 * Dùng khi công tắc Mock bật hoặc khi mọi nhà cung cấp đều lỗi (trả kèm trong `fallback` của lỗi 503).
 */
@Component
public class MockAi {
    public static final String SOURCE = "mock";
    private final ObjectMapper mapper;

    public MockAi(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectNode chat(JsonNode facts) {
        ObjectNode out = mapper.createObjectNode();
        ArrayNode shown = out.putArray("facts");
        StringBuilder answer = new StringBuilder("**Dữ liệu mẫu.** ");
        boolean risk = false;
        JsonNode peaks = facts.path("forecastPeaks");
        for (JsonNode peak : peaks) {
            double pct = peak.path("pct").asDouble();
            shown.addObject().put("label", "Bãi " + peak.path("lotId").asText()).put("value", format(pct) + "%");
            if (pct >= 100) {
                risk = true;
                answer.append("Bãi ").append(peak.path("lotId").asText()).append(" dự báo đạt ").append(format(pct))
                        .append("% lúc ").append(peak.path("time").asText()).append(". ");
            }
        }
        if (peaks.isEmpty()) {
            for (JsonNode lot : facts.path("lots")) {
                double pct = lot.path("pct").asDouble();
                shown.addObject().put("label", "Bãi " + lot.path("lotId").asText()).put("value", format(pct) + "%");
                answer.append("Bãi ").append(lot.path("lotId").asText()).append(" đang ").append(format(pct)).append("%. ");
                if (pct >= 100) risk = true;
            }
            if (shown.isEmpty()) answer.append("Chưa có số liệu về các bãi. ");
            else answer.append("Chưa có dự báo cho các bãi, nên chưa kết luận được về nguy cơ quá tải. ");
        } else if (!risk) {
            answer.append("Chưa bãi nào dự báo vượt sức chứa. ");
        }
        JsonNode plan = facts.path("plan");
        if (plan.isObject()) {
            answer.append("Kế hoạch phiên bản ").append(plan.path("version").asInt()).append(" đang ").append(plan.path("status").asText()).append(". ");
        }
        answer.append("Phương án cần quản trị duyệt trước khi áp dụng.");
        out.put("answer", answer.toString());
        out.putArray("suggestions")
                .add("Sáng mai có bãi nào nguy cơ quá tải không?")
                .add("Tuần này bãi nào căng nhất, vào mấy giờ?")
                .add("Sáng mai cần thêm bảo vệ ở cổng nào?");
        return out;
    }

    /** Một chữ số thập phân, dấu phẩy: cùng kiểu hiển thị với giao diện. */
    static String format(double value) {
        return String.valueOf(Math.round(value * 10) / 10.0).replace('.', ',');
    }
}
