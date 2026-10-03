package com.parkflow.backend.sim;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpikeEventsTest {
    private final ReplayLog.Event original = new ReplayLog.Event("evt_test", Instant.parse("2026-09-22T00:00:00Z"),
            "lot-a", "in", "motorbike", "original-card", Instant.parse("2026-09-22T01:00:00Z"));

    @Test void newVehicleHasMatchingEntryAndExitAndStableIdentifiers() {
        var pair = SpikeEvents.from(original, "lot-a", 100);
        assertEquals(2, pair.size());
        var entry = pair.get(0);
        var exit = pair.get(1);
        assertEquals("in", entry.direction());
        assertEquals("out", exit.direction());
        assertEquals(entry.card(), exit.card());
        assertNotEquals(original.card(), entry.card());
        assertNotEquals(entry.id(), exit.id());
        assertEquals(original.ts().plusSeconds(1), entry.ts());
        assertEquals(original.exit().plusSeconds(1), exit.ts());
        assertEquals(entry.exit(), exit.ts());
        assertEquals(pair, SpikeEvents.from(original, "lot-a", 100));
        assertTrue(SpikeEvents.from(entry, null, 100).isEmpty());
        assertTrue(SpikeEvents.from(exit, null, 100).isEmpty());
    }

    @Test void disabledOrOtherLotDoesNotCreateVehicles() {
        assertTrue(SpikeEvents.from(original, null, 0).isEmpty());
        assertTrue(SpikeEvents.from(original, "lot-b", 100).isEmpty());
        var unpaired = new ReplayLog.Event(original.id(), original.ts(), original.lot(), "in", original.vehicle(), original.card(), null);
        assertTrue(SpikeEvents.from(unpaired, null, 100).isEmpty());
    }

    @Test void twentyPercentSelectsApproximatelyOneInFiveEntries() {
        int selected = 0;
        for (int i = 0; i < 10000; i++) {
            var event = new ReplayLog.Event("evt_" + i, original.ts(), original.lot(), "in", original.vehicle(), original.card(), original.exit());
            if (!SpikeEvents.from(event, null, 20).isEmpty()) selected++;
        }
        assertTrue(selected > 1800 && selected < 2200, "Selected: " + selected);
    }
}
