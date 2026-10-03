package com.parkflow.backend.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.parkflow.backend.common.ParkFlowClock;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Kiểm tra sự kiện quẹt thẻ: ca hợp lệ và từng mã lý do từ chối. Không cần Postgres. */
class EventValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ParkFlowClock clock = mock(ParkFlowClock.class);
    private EventValidator validator;

    @BeforeEach void setup() {
        when(clock.read()).thenReturn(new ParkFlowClock.Reading("sim", OffsetDateTime.parse("2026-10-03T07:20:00+07:00")));
        validator = new EventValidator(clock);
    }

    private String json(String id, String ts, String lot, String direction, String vehicle, String card) {
        return "{\"event_id\":" + q(id) + ",\"ts\":" + q(ts) + ",\"lot_id\":" + q(lot) + ",\"direction\":" + q(direction)
                + ",\"vehicle_type\":" + q(vehicle) + ",\"card_hash\":" + q(card) + "}";
    }

    private static String q(String value) { return value == null ? "null" : "\"" + value + "\""; }

    private EventValidator.Result check(String id, String ts, String lot, String direction, String vehicle, String card) {
        return validator.validate(mapper.readTree(json(id, ts, lot, direction, vehicle, card)));
    }

    @Test void aWellFormedEventIsAccepted() {
        var result = check("evt_1", "2026-10-03T07:12:44+07:00", "A", "in", "motorbike", "e91d4c");
        assertTrue(result.isValid());
        assertEquals("evt_1", result.event().eventId());
        assertEquals("A", result.event().lotId());
        assertEquals(OffsetDateTime.parse("2026-10-03T07:12:44+07:00"), result.event().ts());
    }

    @Test void everyFieldIsRequired() {
        assertEquals("MISSING_EVENT_ID", check(null, "2026-10-03T07:12:44+07:00", "A", "in", "car", "h").reason());
        assertEquals("MISSING_TS", check("e", null, "A", "in", "car", "h").reason());
        assertEquals("MISSING_LOT", check("e", "2026-10-03T07:12:44+07:00", " ", "in", "car", "h").reason());
        assertEquals("BAD_DIRECTION", check("e", "2026-10-03T07:12:44+07:00", "A", null, "car", "h").reason());
        assertEquals("BAD_VEHICLE_TYPE", check("e", "2026-10-03T07:12:44+07:00", "A", "in", null, "h").reason());
        assertEquals("BAD_CARD_HASH", check("e", "2026-10-03T07:12:44+07:00", "A", "in", "car", null).reason());
    }

    @Test void valuesOutsideTheContractAreRejectedWithAStableReason() {
        assertEquals("BAD_TS", check("e", "hôm qua", "A", "in", "car", "h").reason());
        assertEquals("BAD_TS", check("e", "2026-10-03T07:12:44", "A", "in", "car", "h").reason(), "thiếu múi giờ");
        assertEquals("BAD_DIRECTION", check("e", "2026-10-03T07:12:44+07:00", "A", "sideways", "car", "h").reason());
        assertEquals("BAD_VEHICLE_TYPE", check("e", "2026-10-03T07:12:44+07:00", "A", "in", "truck", "h").reason());
    }

    @Test void aTimestampMoreThanADayInTheFutureIsRejected() {
        assertEquals("TS_IN_FUTURE", check("e", "2026-10-05T07:12:44+07:00", "A", "in", "car", "h").reason());
        assertTrue(check("e", "2026-10-04T07:00:00+07:00", "A", "in", "car", "h").isValid(), "trong vòng một ngày vẫn chấp nhận (lệch đồng hồ)");
    }

    @Test void aRawCardNumberIsNeverAccepted() {
        assertEquals("CARD_NOT_HASHED", check("e", "2026-10-03T07:12:44+07:00", "A", "in", "car", "4111111111111111").reason());
        assertTrue(check("e", "2026-10-03T07:12:44+07:00", "A", "in", "car", "9a4f1c0be2d7").isValid(), "mã băm chứa chữ cái là hợp lệ");
    }

    @Test void anOverlongEventIdIsRejectedButStillReported() {
        var result = check("x".repeat(80), "2026-10-03T07:12:44+07:00", "A", "in", "car", "h");
        assertEquals("BAD_EVENT_ID", result.reason());
        assertEquals(64, result.eventId().length());
    }

    @Test void somethingThatIsNotAnObjectIsRejected() {
        assertEquals("NOT_AN_OBJECT", validator.validate(mapper.readTree("\"chuỗi\"")).reason());
        assertEquals("NOT_AN_OBJECT", validator.validate(null).reason());
    }
}
