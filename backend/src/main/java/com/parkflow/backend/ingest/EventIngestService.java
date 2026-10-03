package com.parkflow.backend.ingest;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi một sự kiện quẹt thẻ và cập nhật số xe của bãi trong CÙNG một giao dịch.
 *
 * Hai điểm quan trọng khi nhiều yêu cầu đến cùng lúc:
 * 1) Chống trùng bằng `INSERT ... ON CONFLICT DO NOTHING`: CSDL tự quyết ai vào trước; số dòng thêm được bằng 0 nghĩa là
 *    sự kiện này đã có. Không "kiểm tra rồi mới ghi" vì giữa hai bước đó yêu cầu khác có thể chen vào.
 * 2) Khóa hàng bãi (`FOR UPDATE`) trước khi đổi số xe, để hai sự kiện cùng lúc không ghi đè lên nhau.
 * Số xe thô `raw_count` có thể âm (thiếu lượt vào); số hiển thị `current_count` kẹp trong [0, sức chứa tối đa].
 */
@Service
@RequiredArgsConstructor
public class EventIngestService {

    public enum Outcome { ACCEPTED, DUPLICATE, UNKNOWN_LOT }

    private final JdbcTemplate jdbc;

    @Transactional
    public Outcome ingest(EventValidator.ValidEvent event) {
        var locked = jdbc.queryForList("SELECT lot_id FROM lots WHERE lot_id = ? FOR UPDATE", String.class, event.lotId());
        if (locked.isEmpty()) return Outcome.UNKNOWN_LOT;

        int inserted = jdbc.update("""
                INSERT INTO events (event_id, ts, lot_id, direction, vehicle_type, card_hash)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING
                """, event.eventId(), event.ts(), event.lotId(), event.direction(), event.vehicleType(), event.cardHash());
        if (inserted == 0) return Outcome.DUPLICATE;

        int delta = "in".equals(event.direction()) ? 1 : -1;
        jdbc.update("""
                UPDATE lots SET raw_count = raw_count + ?,
                       current_count = LEAST(max_capacity, GREATEST(0, raw_count + ?)),
                       last_event_at = GREATEST(COALESCE(last_event_at, ?::timestamptz), ?::timestamptz),
                       updated_at = now()
                WHERE lot_id = ?
                """, delta, delta, event.ts(), event.ts(), event.lotId());
        return Outcome.ACCEPTED;
    }
}
