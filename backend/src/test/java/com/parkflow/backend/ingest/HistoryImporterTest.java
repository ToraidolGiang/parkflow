package com.parkflow.backend.ingest;

import com.parkflow.backend.sim.ReplayLog;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HistoryImporterTest {
    @Test void importsInBatchesAndReportsOnlyInsertedRows() {
        var jdbc = mock(JdbcTemplate.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var events = List.of(new ReplayLog.Event("history-test", Instant.parse("2026-09-21T00:00:00Z"),
                "A", "in", "motorbike", "hashed-card", null));
        when(jdbc.queryForList("SELECT lot_id FROM lots", String.class)).thenReturn(List.of("A"));
        when(jdbc.batchUpdate(anyString(), eq(events), eq(500), any(ParameterizedPreparedStatementSetter.class)))
                .thenReturn(new int[][]{{1}}, new int[][]{{0}});
        try (var replay = mockStatic(ReplayLog.class)) {
            replay.when(ReplayLog::history).thenReturn(events);
            var importer = new HistoryImporter(jdbc, publisher);
            assertEquals(1, importer.importPackaged());
            assertEquals(0, importer.importPackaged());
            verify(jdbc, times(2)).execute("SELECT pg_advisory_xact_lock(20261001)");
            verify(jdbc, times(2)).batchUpdate(contains("ON CONFLICT(event_id) DO NOTHING"), eq(events), eq(500), any(ParameterizedPreparedStatementSetter.class));
            verify(publisher).publishEvent(new HistoryImporter.Imported(1));
            verify(publisher).publishEvent(new HistoryImporter.Imported(0));
        }
    }

    @Test void unknownLotFailsBeforeWriting() {
        var jdbc = mock(JdbcTemplate.class);
        var publisher = mock(ApplicationEventPublisher.class);
        when(jdbc.queryForList("SELECT lot_id FROM lots", String.class)).thenReturn(List.of("A"));
        try (var replay = mockStatic(ReplayLog.class)) {
            replay.when(ReplayLog::history).thenReturn(List.of(new ReplayLog.Event("history-unknown",
                    Instant.parse("2026-09-21T00:00:00Z"), "UNKNOWN", "in", "car", "hashed-card", null)));
            assertThrows(IllegalStateException.class, () -> new HistoryImporter(jdbc, publisher).importPackaged());
            verify(jdbc, never()).execute(anyString());
            verify(jdbc, never()).batchUpdate(anyString(), anyCollection(), anyInt(), any(ParameterizedPreparedStatementSetter.class));
            verifyNoInteractions(publisher);
        }
    }
}
