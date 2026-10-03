package com.parkflow.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.parkflow.backend.domain.Lot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Lược đồ V1 khớp thực thể (Hibernate kiểm `validate`) và các ràng buộc cốt lõi của CSDL. Cần Postgres; mỗi test rollback. */
@SpringBootTest
@Transactional
class LotRepositoryTest {
    @Autowired LotRepository lots;
    @Autowired JdbcTemplate jdbc;

    private Lot lot(String id, int capacity, int max) {
        Lot lot = new Lot();
        lot.setLotId(id);
        lot.setName("Bãi " + id);
        lot.setCapacity(capacity);
        lot.setMaxCapacity(max);
        lot.setGate("MAIN");
        return lot;
    }

    @Test void aSavedLotComesBackWithZeroCountsByDefault() {
        lots.saveAndFlush(lot("T1", 500, 600));
        Lot found = lots.findById("T1").orElseThrow();
        assertEquals("Bãi T1", found.getName());
        assertEquals(0, found.getCurrentCount());
        assertEquals(0, found.getRawCount());
    }

    @Test void capacityMustBePositiveAndMaxAtLeastCapacity() {
        assertThrows(DataIntegrityViolationException.class, () -> lots.saveAndFlush(lot("T2", 0, 10)));
    }

    @Test void anEventNeedsAKnownLot() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO events(event_id, ts, lot_id, direction, vehicle_type, card_hash) VALUES ('e2', now(), 'ZZ', 'in', 'motorbike', 'h')"));
    }

    @Test void anEventDirectionIsInOrOut() {
        lots.saveAndFlush(lot("T3", 100, 120));
        jdbc.update("INSERT INTO events(event_id, ts, lot_id, direction, vehicle_type, card_hash) VALUES ('e1', now(), 'T3', 'in', 'motorbike', 'h')");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE lot_id = 'T3'", Integer.class));
    }

    @Test void anUnknownDirectionIsRejected() {
        lots.saveAndFlush(lot("T5", 100, 120));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO events(event_id, ts, lot_id, direction, vehicle_type, card_hash) VALUES ('e3', now(), 'T5', 'sideways', 'motorbike', 'h')"));
    }

    @Test void theSameEventIdCannotBeStoredTwice() {
        lots.saveAndFlush(lot("T4", 100, 120));
        jdbc.update("INSERT INTO events(event_id, ts, lot_id, direction, vehicle_type, card_hash) VALUES ('dup', now(), 'T4', 'in', 'motorbike', 'h')");
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO events(event_id, ts, lot_id, direction, vehicle_type, card_hash) VALUES ('dup', now(), 'T4', 'out', 'motorbike', 'h')"));
    }
}
