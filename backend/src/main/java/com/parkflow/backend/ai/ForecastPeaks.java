package com.parkflow.backend.ai;

import com.parkflow.backend.common.ApiException;
import com.parkflow.backend.common.ParkFlowClock;
import com.parkflow.backend.domain.Lot;
import com.parkflow.backend.forecast.ForecastMath;
import com.parkflow.backend.forecast.ForecastService;
import com.parkflow.backend.repository.LotRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Đỉnh dự báo từng bãi cho trợ lý, lấy từ chính {@link ForecastService} mà trang Dự báo dùng, để chữ trả lời và biểu đồ khớp một nguồn số.
 * Trước 13:00 hỏi về hôm nay; từ sau khung dự báo 06:00–13:00 thì hỏi về sáng hôm sau. Bãi chưa có lịch sử thì bỏ qua (không bịa số).
 */
@Component
@RequiredArgsConstructor
public class ForecastPeaks implements ForecastPeakSource {
    private final ForecastService forecast;
    private final LotRepository lots;
    private final ParkFlowClock clock;

    @Override
    public List<Peak> peaks() {
        var now = clock.read().now();
        LocalDate date = now.toLocalTime().isAfter(ForecastMath.END) ? now.toLocalDate().plusDays(1) : now.toLocalDate();
        List<Peak> peaks = new ArrayList<>();
        for (Lot lot : lots.findAll()) {
            try {
                var result = forecast.getForecast(lot.getLotId(), date);
                peaks.add(new Peak(lot.getLotId(), result.peak().t(), result.peak().count(), result.peak().pct()));
            } catch (ApiException e) {
                // NO_HISTORY hoặc bãi không còn: không có dự báo cho bãi này, trợ lý sẽ nói rõ là chưa có.
            }
        }
        peaks.sort(Comparator.comparingDouble(Peak::pct).reversed());
        return peaks;
    }
}
