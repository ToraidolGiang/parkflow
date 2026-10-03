package com.parkflow.backend.ingest;

import com.parkflow.backend.common.ParkFlowClock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Kiểm tra một sự kiện quẹt thẻ trước khi ghi (hợp đồng: docs/thiet-ke/04-api.md mục 1).
 * Sự kiện sai không làm hỏng cả lô: được trả về trong `rejected` kèm mã lý do ổn định.
 */
@Component
public class EventValidator {
    static final Set<String> DIRECTIONS = Set.of("in", "out");
    static final Set<String> VEHICLE_TYPES = Set.of("motorbike", "bicycle", "car");
    /** Số thẻ gốc (chuỗi toàn chữ số dài 8 đến 19) không được vào hệ thống: chỉ nhận mã đã băm. */
    private static final Pattern RAW_CARD_NUMBER = Pattern.compile("^[0-9]{8,19}$");
    private static final Duration MAX_FUTURE = Duration.ofDays(1);

    /** Sự kiện đã hợp lệ, dạng gọn để ghi. */
    public record ValidEvent(String eventId, OffsetDateTime ts, String lotId, String direction, String vehicleType, String cardHash) {}

    /** `reason` rỗng khi hợp lệ; `eventId` có thể rỗng nếu chính trường này sai. */
    public record Result(String eventId, String reason, ValidEvent event) {
        public boolean isValid() { return reason == null; }
    }

    private final ParkFlowClock clock;

    public EventValidator(ParkFlowClock clock) {
        this.clock = clock;
    }

    public Result validate(JsonNode item) {
        if (item == null || !item.isObject()) return reject(null, "NOT_AN_OBJECT");
        String eventId = text(item, "event_id");
        if (eventId == null) return reject(null, "MISSING_EVENT_ID");
        if (eventId.length() > 64) return reject(eventId.substring(0, 64), "BAD_EVENT_ID");

        OffsetDateTime ts;
        String rawTs = text(item, "ts");
        if (rawTs == null) return reject(eventId, "MISSING_TS");
        try {
            ts = OffsetDateTime.parse(rawTs);
        } catch (DateTimeParseException e) {
            return reject(eventId, "BAD_TS");
        }
        if (ts.toInstant().isAfter(clock.read().now().toInstant().plus(MAX_FUTURE))) return reject(eventId, "TS_IN_FUTURE");

        String lotId = text(item, "lot_id");
        if (lotId == null) return reject(eventId, "MISSING_LOT");
        if (lotId.length() > 20) return reject(eventId, "UNKNOWN_LOT");

        String direction = text(item, "direction");
        if (direction == null || !DIRECTIONS.contains(direction)) return reject(eventId, "BAD_DIRECTION");

        String vehicleType = text(item, "vehicle_type");
        if (vehicleType == null || !VEHICLE_TYPES.contains(vehicleType)) return reject(eventId, "BAD_VEHICLE_TYPE");

        String cardHash = text(item, "card_hash");
        if (cardHash == null || cardHash.length() > 128) return reject(eventId, "BAD_CARD_HASH");
        if (RAW_CARD_NUMBER.matcher(cardHash).matches()) return reject(eventId, "CARD_NOT_HASHED");

        return new Result(eventId, null, new ValidEvent(eventId, ts, lotId, direction, vehicleType, cardHash));
    }

    private static Result reject(String eventId, String reason) {
        return new Result(eventId, reason, null);
    }

    /** Chuỗi không rỗng sau khi cắt khoảng trắng; null nếu thiếu, không phải chuỗi hoặc rỗng. */
    private static String text(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || !node.isString()) return null;
        String value = node.asString().trim();
        return value.isEmpty() ? null : value;
    }
}
