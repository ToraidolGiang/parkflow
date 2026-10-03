package com.parkflow.backend.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ghi sự kiện, chống trùng và số xe của bãi, kể cả khi nhiều yêu cầu cùng lúc. Cần Postgres; dọn dữ liệu của chính test. */
@SpringBootTest
class EventIngestServiceTest {
    private static final String LOT = "ING1";
    @Autowired EventIngestService ingest;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void setup() {
        cleanup();
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate) VALUES (?, 'Bãi thử', 100, 120, 'MAIN')", LOT);
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM events WHERE lot_id = ?", LOT);
        jdbc.update("DELETE FROM lots WHERE lot_id = ?", LOT);
    }

    private static EventValidator.ValidEvent event(String id, String direction) {
        return new EventValidator.ValidEvent(id, OffsetDateTime.parse("2026-10-03T07:12:44+07:00"), LOT, direction, "motorbike", "hash-" + id);
    }

    private int count() { return jdbc.queryForObject("SELECT current_count FROM lots WHERE lot_id = ?", Integer.class, LOT); }

    private int raw() { return jdbc.queryForObject("SELECT raw_count FROM lots WHERE lot_id = ?", Integer.class, LOT); }

    @Test void anAcceptedEntryRaisesTheCountAndAnExitLowersIt() {
        assertEquals(EventIngestService.Outcome.ACCEPTED, ingest.ingest(event("a1", "in")));
        assertEquals(EventIngestService.Outcome.ACCEPTED, ingest.ingest(event("a2", "in")));
        assertEquals(2, count());
        assertEquals(EventIngestService.Outcome.ACCEPTED, ingest.ingest(event("a3", "out")));
        assertEquals(1, count());
    }

    @Test void theSameEventSentTwiceIsCountedOnce() {
        assertEquals(EventIngestService.Outcome.ACCEPTED, ingest.ingest(event("dup", "in")));
        assertEquals(EventIngestService.Outcome.DUPLICATE, ingest.ingest(event("dup", "in")));
        assertEquals(1, count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE event_id = 'dup'", Integer.class));
    }

    @Test void anUnknownLotIsReportedAndNothingIsWritten() {
        var stray = new EventValidator.ValidEvent("x1", OffsetDateTime.parse("2026-10-03T07:12:44+07:00"), "NOPE", "in", "car", "h");
        assertEquals(EventIngestService.Outcome.UNKNOWN_LOT, ingest.ingest(stray));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE event_id = 'x1'", Integer.class));
    }

    @Test void theDisplayedCountNeverGoesBelowZeroButTheRawCountKeepsTheTruth() {
        ingest.ingest(event("o1", "out"));
        ingest.ingest(event("o2", "out"));
        assertEquals(0, count());
        assertEquals(-2, raw());
        ingest.ingest(event("i1", "in"));
        assertEquals(0, count(), "còn thiếu 2 lượt vào nên số hiển thị vẫn 0");
        assertEquals(-1, raw());
    }

    @Test void theDisplayedCountIsCappedAtTheMaximumCapacity() {
        for (int i = 0; i < 125; i++) ingest.ingest(event("m" + i, "in"));
        assertEquals(120, count());
        assertEquals(125, raw());
    }

    @Test void theLastEventTimeOnlyMovesForward() {
        ingest.ingest(new EventValidator.ValidEvent("t1", OffsetDateTime.parse("2026-10-03T08:00:00+07:00"), LOT, "in", "car", "h1"));
        ingest.ingest(new EventValidator.ValidEvent("t2", OffsetDateTime.parse("2026-10-03T07:00:00+07:00"), LOT, "in", "car", "h2"));
        var last = jdbc.queryForObject("SELECT last_event_at FROM lots WHERE lot_id = ?", OffsetDateTime.class, LOT);
        assertEquals(OffsetDateTime.parse("2026-10-03T08:00:00+07:00").toInstant(), last.toInstant());
    }

    @Test void manyEventsAtTheSameTimeAreAllCountedExactlyOnce() throws Exception {
        var pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<EventIngestService.Outcome>> jobs = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                String id = "c" + (i % 40);   // 60 yêu cầu cho 40 mã: 20 yêu cầu là gửi lại
                jobs.add(() -> ingest.ingest(event(id, "in")));
            }
            int accepted = 0;
            for (Future<EventIngestService.Outcome> f : pool.invokeAll(jobs)) if (f.get() == EventIngestService.Outcome.ACCEPTED) accepted++;
            assertEquals(40, accepted);
            assertEquals(40, count());
            assertEquals(40, raw());
        } finally {
            pool.shutdownNow();
        }
    }
}
