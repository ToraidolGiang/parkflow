package com.parkflow.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bãi xe. `capacity` là sức chứa thiết kế (100%); số xe hiển thị `currentCount` đã kẹp không âm, `rawCount` là số tính thô từ sự kiện. */
@Entity
@Table(name = "lots")
@Getter
@Setter
@NoArgsConstructor
public class Lot {
    @Id
    @Column(name = "lot_id", length = 20)
    private String lotId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    @Column(length = 20)
    private String gate;

    @Column(name = "current_count", nullable = false)
    private Integer currentCount = 0;

    @Column(name = "raw_count", nullable = false)
    private Integer rawCount = 0;

    @Column(name = "last_event_at")
    private OffsetDateTime lastEventAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
