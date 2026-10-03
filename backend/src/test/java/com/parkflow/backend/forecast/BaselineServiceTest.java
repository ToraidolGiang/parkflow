package com.parkflow.backend.forecast;

import com.parkflow.backend.common.ParkFlowClock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class BaselineServiceTest {
    private static final LocalDate TARGET = LocalDate.of(2026, 10, 3);
    @Autowired JdbcTemplate jdbc;
    @Autowired BaselineService service;
    private String lot;

    @BeforeEach void createOwnLot() {
        lot = "BL" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO lots(lot_id,name,capacity,max_capacity) VALUES (?, ?, 10, 12)", lot, lot);
    }

    private void event(LocalDate day, String time, String direction, String card, boolean historical) {
        OffsetDateTime ts = day.atTime(LocalTime.parse(time)).atZone(ParkFlowClock.ZONE).toOffsetDateTime();
        jdbc.update("""
                INSERT INTO events(event_id,ts,lot_id,direction,vehicle_type,card_hash,historical)
                VALUES (?,?,?,?,'motorbike',?,?)
                """, UUID.randomUUID().toString(), ts, lot, direction, card, historical);
    }

    private void arrivals(LocalDate day, String time, int count, boolean historical) {
        for (int i = 0; i < count; i++) event(day, time, "in", time + i, historical);
    }

    @Test void noHistoryProducesNoInventedCurve() {
        assertTrue(service.baseline(lot, TARGET).isEmpty());
        arrivals(TARGET.minusWeeks(1), "07:00", 2, false);
        arrivals(TARGET.minusDays(1), "07:00", 2, true);
        assertTrue(service.baseline(lot, TARGET).isEmpty());
    }

    @Test void usesFourMostRecentObservedWeekdaysAndHistoricalEventsOnly() {
        int[] counts = {2, 4, 6, 100};
        for (int i = 0; i < counts.length; i++) arrivals(TARGET.minusWeeks(i + 1), "06:15", counts[i], true);
        arrivals(TARGET.minusWeeks(5), "06:15", 100, true);
        arrivals(TARGET.minusDays(1), "06:15", 100, true);
        arrivals(TARGET, "06:15", 100, true);
        arrivals(TARGET.plusWeeks(1), "06:15", 100, true);
        arrivals(TARGET.minusWeeks(1), "06:15", 100, false);
        var result = service.baseline(lot, TARGET);
        assertEquals(29, result.size());
        assertEquals(LocalTime.of(6, 0), result.get(0).t());
        assertEquals(LocalTime.of(13, 0), result.get(28).t());
        assertEquals(0, result.get(0).count());
        assertEquals(5, result.get(1).count());
        assertEquals(5, result.get(28).count());
    }

    @Test void takesMedianAfterAccumulationRatherThanAddingMediansOfFlows() {
        LocalDate first = TARGET.minusWeeks(1), second = TARGET.minusWeeks(2), third = TARGET.minusWeeks(3);
        arrivals(first, "06:15", 10, true);
        arrivals(second, "06:30", 10, true);
        // A recorded departure provides an observed third day with zero occupancy.
        event(third, "14:00", "out", "missing-in", true);
        var result = service.baseline(lot, TARGET);
        assertEquals(0, result.get(1).count());
        assertEquals(10, result.get(2).count());
    }

    @Test void includesOpeningSessionsAndPreSixEventsWithExactSlotBoundaries() {
        LocalDate day = TARGET.minusWeeks(1);
        event(day.minusDays(1), "23:00", "in", "overnight", true);
        event(day.minusDays(1), "22:00", "in", "closed", true);
        event(day.minusDays(1), "23:00", "out", "closed", true);
        event(day.minusDays(2), "23:59", "in", "expired", true);
        event(day.minusDays(1), "23:00", "in", "live", false);
        event(day, "05:00", "in", "early", true);
        event(day, "06:00", "out", "overnight", true);
        event(day, "06:15:00", "in", "boundary", true);
        event(day, "06:15:01", "in", "later", true);
        event(day, "13:00", "out", "early", true);
        event(day, "13:00:01", "in", "after", true);
        var result = service.baseline(lot, TARGET);
        assertEquals(1, result.get(0).count());
        assertEquals(2, result.get(1).count());
        assertEquals(3, result.get(2).count());
        assertEquals(2, result.get(28).count());
    }

    @Test void missingWeeksAreNotZeroFilledAndOverloadIsNotCapped() {
        arrivals(TARGET.minusWeeks(2), "06:00", 20, true);
        arrivals(TARGET.minusWeeks(6), "06:00", 21, true);
        assertEquals(20.5, service.baseline(lot, TARGET).get(0).count());
    }

    @Test void negativeCountsAreClampedAndOtherLotsAreExcluded() {
        event(TARGET.minusWeeks(1), "06:00", "out", "missing-in", true);
        assertEquals(0, service.baseline(lot, TARGET).get(0).count());
        assertTrue(service.baseline("UNKNOWN_BASELINE", TARGET).isEmpty());
    }
}
