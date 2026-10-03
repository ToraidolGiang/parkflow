package com.parkflow.backend.quality;

import com.parkflow.backend.common.ParkFlowClock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Kiểm tra chất lượng dữ liệu sự kiện của từng bãi và trả cờ theo hợp đồng H12.
 *
 * <h3>Ba cờ được kiểm tra:</h3>
 * <ol>
 *   <li><b>missing_out</b> – {@code raw_count} âm sâu ({@code <= -MISSING_OUT_THRESHOLD}):
 *       nhiều xe vào nhưng cổng ra không ghi nhận lượt ra tương ứng.</li>
 *   <li><b>implausible_count</b> – {@code current_count >= max_capacity}:
 *       số xe đang vượt sức chứa tối đa; gây nghi ngờ cảm biến bị trôi.</li>
 *   <li><b>signal_lost</b> – bãi không nhận sự kiện nào trong {@code SIGNAL_LOST_MINUTES} phút
 *       trong khung giờ hoạt động ({@code OPEN_HOUR}–{@code CLOSE_HOUR}).</li>
 * </ol>
 *
 * <h3>Phương thức công khai để Giang nối:</h3>
 * <pre>
 *   Map&lt;String, LotQuality&gt; flagsByLot = qualityService.flagsByLot();
 *   String dataFlag = flagsByLot.getOrDefault(lotId, LotQuality.OK).primaryFlag();
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class QualityService {

    // ── Ngưỡng (H14: đánh giá sau khi chạy thử đột biến) ────────────────────
    /**
     * raw_count âm dưới ngưỡng này → cờ missing_out.
     * H14: spike 20% tạo ra raw_count khoảng -8 đến -15 trong 15 phút;
     * ngưỡng -5 phát hiện đúng và không false-positive ở bãi bình thường.
     * GIỮ NGUYÊN.
     */
    static final int MISSING_OUT_THRESHOLD = -5;

    /**
     * Số phút không nhận sự kiện trong giờ hoạt động → cờ signal_lost.
     * H14: rush-hour có sự kiện mỗi vài giây; 30 phút im là đủ đáng lo.
     * Ngoài giờ hoạt động kiểm tra tự bỏ qua. GIỮ NGUYÊN.
     */
    static final int SIGNAL_LOST_MINUTES = 30;

    /** Giờ mở cửa hoạt động (bãi được coi là im lặng bất thường khi trong khoảng này). */
    static final LocalTime OPEN_HOUR  = LocalTime.of(6, 0);
    static final LocalTime CLOSE_HOUR = LocalTime.of(22, 0);
    // ─────────────────────────────────────────────────────────────────────────

    private final JdbcTemplate jdbc;
    private final ParkFlowClock clock;

    /**
     * Tính cờ chất lượng cho <em>tất cả</em> bãi.
     * Kết quả được lập index theo {@code lotId} để Giang tra cứu nhanh từ {@code lots/status}.
     *
     * @return map lotId → LotQuality; không bao giờ null.
     */
    public Map<String, LotQuality> flagsByLot() {
        OffsetDateTime now = clock.read().now();
        OffsetDateTime threshold = now.minusMinutes(SIGNAL_LOST_MINUTES);

        var rows = jdbc.queryForList("""
                SELECT lot_id, raw_count, current_count, max_capacity, last_event_at
                FROM lots ORDER BY lot_id
                """);

        Map<String, LotQuality> result = new LinkedHashMap<>();
        for (var row : rows) {
            String lotId = (String) row.get("lot_id");
            int rawCount = ((Number) row.get("raw_count")).intValue();
            int currentCount = ((Number) row.get("current_count")).intValue();
            int maxCapacity = ((Number) row.get("max_capacity")).intValue();
            Object lastEventAtObj = row.get("last_event_at");
            OffsetDateTime lastEventAt = lastEventAtObj == null ? null
                    : ((java.sql.Timestamp) lastEventAtObj).toInstant()
                            .atZone(ParkFlowClock.ZONE).toOffsetDateTime();

            List<QualityFlag> flags = new ArrayList<>();
            Map<String, Object> details = new LinkedHashMap<>();

            // ── Cờ signal_lost ────────────────────────────────────────────────
            LocalTime nowTime = now.toLocalTime();
            boolean inOperatingHours = !nowTime.isBefore(OPEN_HOUR) && !nowTime.isAfter(CLOSE_HOUR);
            if (inOperatingHours) {
                boolean lost = (lastEventAt == null) || lastEventAt.isBefore(threshold);
                if (lost) {
                    flags.add(QualityFlag.signal_lost);
                    Map<String, Object> slDetail = new java.util.LinkedHashMap<>();
                    slDetail.put("lastEventAt", lastEventAt == null ? null : lastEventAt.toString());
                    slDetail.put("silentMinutes", lastEventAt == null ? null
                            : java.time.Duration.between(lastEventAt, now).toMinutes());
                    slDetail.put("thresholdMinutes", SIGNAL_LOST_MINUTES);
                    details.put("signal_lost", slDetail);
                }
            }

            // ── Cờ missing_out ───────────────────────────────────────────────
            if (rawCount <= MISSING_OUT_THRESHOLD) {
                flags.add(QualityFlag.missing_out);
                details.put("missing_out", Map.of(
                        "rawCount", rawCount,
                        "threshold", MISSING_OUT_THRESHOLD));
            }

            // ── Cờ implausible_count ─────────────────────────────────────────
            if (currentCount >= maxCapacity) {
                flags.add(QualityFlag.implausible_count);
                details.put("implausible_count", Map.of(
                        "currentCount", currentCount,
                        "maxCapacity", maxCapacity));
            }

            if (flags.isEmpty()) flags.add(QualityFlag.ok);
            result.put(lotId, new LotQuality(lotId, List.copyOf(flags), Map.copyOf(details)));
        }
        return result;
    }

    /**
     * Tiện ích để LotStatusService (Giang) gọi nhanh cho một bãi đơn lẻ.
     *
     * @param lotId mã bãi cần tra cứu.
     * @return {@link LotQuality} của bãi; {@code LotQuality} với cờ {@code ok} nếu không tìm thấy.
     */
    public LotQuality flagForLot(String lotId) {
        return flagsByLot().getOrDefault(lotId,
                new LotQuality(lotId, List.of(QualityFlag.ok), Map.of()));
    }
}
