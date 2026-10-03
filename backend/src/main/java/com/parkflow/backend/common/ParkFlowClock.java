package com.parkflow.backend.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Đồng hồ duy nhất của hệ thống. Khi trình giả lập đang chạy trả giờ mô phỏng (`mode` = "sim"), không thì giờ thật ("real").
 * Mọi tính toán theo ngày và giờ (dự báo, kế hoạch, gợi ý) lấy giờ từ đây để demo ngày 22/09 vẫn cho kết quả đúng ngày đó.
 */
@Component
public class ParkFlowClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** `mode` là "sim" hoặc "real". */
    public record Reading(String mode, OffsetDateTime now) {}

    private final ObjectProvider<SimulationTimeSource> simulation;

    public ParkFlowClock(ObjectProvider<SimulationTimeSource> simulation) {
        this.simulation = simulation;
    }

    public Reading read() {
        SimulationTimeSource source = simulation.getIfAvailable();
        var simulated = source == null ? java.util.Optional.<Instant>empty() : source.currentTime();
        return new Reading(simulated.isPresent() ? "sim" : "real", simulated.orElseGet(Instant::now).atZone(ZONE).toOffsetDateTime());
    }
}
