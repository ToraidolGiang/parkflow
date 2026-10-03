package com.parkflow.backend.ingest;

import com.parkflow.backend.sim.ReplayLog;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class HistoryImporterDatabaseTest {
    @Autowired HistoryImporter importer;
    @Autowired JdbcTemplate jdbc;

    @Test void repeatedImportDoesNotDuplicateOrChangeLiveCounts() {
        jdbc.update("INSERT INTO lots(lot_id,name,capacity,max_capacity,current_count,raw_count,gate) VALUES ('HIST_TEST','History test',100,120,7,7,'MAIN')");
        var event = new ReplayLog.Event("hist_test_entry", Instant.parse("2026-09-21T00:00:00Z"), "HIST_TEST", "in", "motorbike", "hashed-test-card", null);
        try (var replay = mockStatic(ReplayLog.class)) {
            replay.when(ReplayLog::history).thenReturn(List.of(event));
            assertEquals(1, importer.importPackaged());
            assertEquals(0, importer.importPackaged());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM events WHERE event_id='hist_test_entry' AND historical", Integer.class));
            assertEquals(7, jdbc.queryForObject("SELECT current_count FROM lots WHERE lot_id='HIST_TEST'", Integer.class));
            assertEquals(7, jdbc.queryForObject("SELECT raw_count FROM lots WHERE lot_id='HIST_TEST'", Integer.class));
            assertNull(jdbc.queryForObject("SELECT last_event_at FROM lots WHERE lot_id='HIST_TEST'", java.time.OffsetDateTime.class));
        }
    }
}
