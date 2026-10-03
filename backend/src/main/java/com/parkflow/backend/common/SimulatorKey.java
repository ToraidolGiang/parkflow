package com.parkflow.backend.common;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Khóa nội bộ để trình giả lập tự gửi sự kiện vào cổng nhận. Không đặt SIMULATOR_API_KEY thì sinh khóa ngẫu nhiên mỗi lần
 * khởi động, nên không có khóa mặc định đoán được và không cần cấu hình thủ công cho khớp với EVENTS_API_KEY.
 */
@Component
public class SimulatorKey {
    private final String value;

    public SimulatorKey(@Value("${parkflow.sim.api-key:}") String configured) {
        this.value = configured == null || configured.isBlank() ? UUID.randomUUID().toString() : configured.trim();
    }

    public String value() {
        return value;
    }
}
