package com.parkflow.backend.common;

import java.time.Instant;
import java.util.Optional;

/** Nguồn thời gian giả lập: trình giả lập cài đặt interface này; khi đang chạy thì mọi nơi dùng giờ mô phỏng thay vì giờ thật. */
@FunctionalInterface
public interface SimulationTimeSource {
    /** Giờ mô phỏng hiện tại, rỗng khi giả lập không chạy. */
    Optional<Instant> currentTime();
}
