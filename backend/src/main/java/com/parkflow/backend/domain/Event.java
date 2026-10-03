package com.parkflow.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sự kiện quẹt thẻ vào hoặc ra một bãi. `eventId` do nhà cung cấp tạo và là khóa chống trùng; `cardHash` là mã thẻ đã băm. */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event {
    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(nullable = false)
    private OffsetDateTime ts;

    @Column(name = "lot_id", nullable = false, length = 20)
    private String lotId;

    /** `in` hoặc `out`. */
    @Column(nullable = false, length = 3)
    private String direction;

    @Column(name = "vehicle_type", nullable = false, length = 20)
    private String vehicleType;

    @Column(name = "card_hash", nullable = false, length = 128)
    private String cardHash;

    /** Sự kiện nạp từ lịch sử (không phải phát trực tiếp). */
    @Column(nullable = false)
    private Boolean historical = false;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();
}
