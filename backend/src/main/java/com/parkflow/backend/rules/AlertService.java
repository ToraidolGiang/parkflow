package com.parkflow.backend.rules;

import com.parkflow.backend.common.ParkFlowClock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Lưu và đọc cảnh báo do quy tắc kích hoạt. Mỗi (quy tắc, bãi) chỉ có một cảnh báo mỗi ngày: gọi lại trong ngày không tạo thêm. */
@Service
@RequiredArgsConstructor
public class AlertService {
    public record Alert(long id, long ruleId, String at, String lotId, String message) {}

    private final JdbcTemplate jdbc;

    /** Trả true nếu vừa tạo cảnh báo mới, false nếu hôm nay đã có. */
    public boolean fire(long ruleId, String lotId, String message, OffsetDateTime at) {
        LocalDate day = at.atZoneSameInstant(ParkFlowClock.ZONE).toLocalDate();
        String text = message.length() > 300 ? message.substring(0, 300) : message;
        return jdbc.update("""
                INSERT INTO alerts (rule_id, at, day, lot_id, message) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (rule_id, lot_id, day) DO NOTHING
                """, ruleId, at, day, lotId, text) > 0;
    }

    /** Mới nhất trước; `limit` được kẹp trong [1, 100]. */
    public List<Alert> latest(int limit) {
        int bounded = Math.max(1, Math.min(100, limit));
        return jdbc.query("SELECT id, rule_id, at, lot_id, message FROM alerts ORDER BY at DESC, id DESC LIMIT ?",
                (rs, i) -> new Alert(rs.getLong("id"), rs.getLong("rule_id"),
                        rs.getTimestamp("at").toInstant().atZone(ParkFlowClock.ZONE).toOffsetDateTime().toString(), rs.getString("lot_id"),
                        rs.getString("message")), bounded);
    }
}
