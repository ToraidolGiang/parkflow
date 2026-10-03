package com.parkflow.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.parkflow.backend.common.ApiException;
import com.parkflow.backend.common.ParkFlowClock;
import com.parkflow.backend.domain.Lot;
import com.parkflow.backend.forecast.ForecastResponse;
import com.parkflow.backend.forecast.ForecastService;
import com.parkflow.backend.repository.LotRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Đỉnh dự báo cho trợ lý: lấy từ dịch vụ dự báo, sắp theo phần trăm, bỏ bãi chưa có lịch sử. Không cần Postgres. */
class ForecastPeaksTest {
    private final ForecastService forecast = mock(ForecastService.class);
    private final LotRepository lots = mock(LotRepository.class);
    private final ParkFlowClock clock = mock(ParkFlowClock.class);
    private final ForecastPeaks source = new ForecastPeaks(forecast, lots, clock);

    private static Lot lot(String id) {
        var lot = new Lot();
        lot.setLotId(id);
        return lot;
    }

    private static ForecastResponse response(String lotId, LocalDate date, String time, int count, double pct) {
        return new ForecastResponse(lotId, date, 1.0, OffsetDateTime.now(), new ForecastResponse.Peak(time, count, pct), List.of(),
                new ForecastResponse.Thresholds(80, 100, 120));
    }

    private void now(String iso) { when(clock.read()).thenReturn(new ParkFlowClock.Reading("real", OffsetDateTime.parse(iso))); }

    @Test void beforeOneInTheAfternoonItAsksAboutTodayAndSortsByPercentage() {
        now("2026-10-03T07:20:00+07:00");
        var today = LocalDate.parse("2026-10-03");
        when(lots.findAll()).thenReturn(List.of(lot("B"), lot("A")));
        when(forecast.getForecast("A", today)).thenReturn(response("A", today, "07:45", 605, 121.0));
        when(forecast.getForecast("B", today)).thenReturn(response("B", today, "07:30", 300, 60.0));
        var peaks = source.peaks();
        assertEquals("A", peaks.get(0).lotId());
        assertEquals(121.0, peaks.get(0).pct());
        assertEquals("07:45", peaks.get(0).time());
        assertEquals("B", peaks.get(1).lotId());
    }

    @Test void afterTheForecastWindowItAsksAboutTomorrow() {
        now("2026-10-03T15:00:00+07:00");
        var tomorrow = LocalDate.parse("2026-10-04");
        when(lots.findAll()).thenReturn(List.of(lot("A")));
        when(forecast.getForecast(eq("A"), eq(tomorrow))).thenReturn(response("A", tomorrow, "07:45", 500, 100.0));
        assertEquals(1, source.peaks().size());
    }

    @Test void aLotWithoutHistoryIsSkippedInsteadOfInventingANumber() {
        now("2026-10-03T07:20:00+07:00");
        when(lots.findAll()).thenReturn(List.of(lot("A"), lot("C")));
        when(forecast.getForecast(eq("A"), any())).thenReturn(response("A", LocalDate.parse("2026-10-03"), "07:45", 500, 100.0));
        when(forecast.getForecast(eq("C"), any())).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "NO_HISTORY", "Chưa có lịch sử"));
        var peaks = source.peaks();
        assertEquals(1, peaks.size());
        assertEquals("A", peaks.get(0).lotId());
    }
}
