package com.parkflow.backend.ingest;

import com.parkflow.backend.sim.ReplayLog;
import java.sql.Timestamp;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, idempotent history import; never mutates live sessions, counts, or heartbeat. */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryImporter {
    private final JdbcTemplate jdbc;
    private final org.springframework.context.ApplicationEventPublisher publisher;
    public record Imported(int count) {}

    @Transactional
    public int importPackaged() {
        var events=ReplayLog.history();
        var lots=new HashSet<>(jdbc.queryForList("SELECT lot_id FROM lots",String.class));
        if(events.stream().anyMatch(e->!lots.contains(e.lot())))
            throw new IllegalStateException("Lịch sử chứa bãi chưa được cấu hình trong CSDL.");
        jdbc.execute("SELECT pg_advisory_xact_lock(20261001)");
        int[][] batches=jdbc.batchUpdate("""
                INSERT INTO events(event_id,ts,lot_id,direction,vehicle_type,card_hash,historical)
                VALUES (?,?,?,?,?,?,TRUE) ON CONFLICT(event_id) DO NOTHING
                """,events,500,(ps,e)->{
            ps.setString(1,e.id());ps.setTimestamp(2,Timestamp.from(e.ts()));ps.setString(3,e.lot());
            ps.setString(4,e.direction());ps.setString(5,e.vehicle());ps.setString(6,e.card());
        });
        int imported=Arrays.stream(batches).flatMapToInt(Arrays::stream).map(n->Math.max(n,0)).sum();
        log.info("Nạp lịch sử: {} sự kiện mới / {} sự kiện; số xe hiện tại giữ nguyên",imported,events.size());
        publisher.publishEvent(new Imported(imported));
        return imported;
    }
}
