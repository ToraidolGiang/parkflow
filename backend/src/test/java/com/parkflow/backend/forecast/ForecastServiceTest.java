package com.parkflow.backend.forecast;

import com.parkflow.backend.common.*;
import com.parkflow.backend.domain.Lot;
import com.parkflow.backend.repository.LotRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ForecastServiceTest {
    BaselineService baseline = mock(BaselineService.class);
    LotRepository lots = mock(LotRepository.class);
    ParkFlowClock clock = mock(ParkFlowClock.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ForecastService service = new ForecastService(baseline, lots, clock, jdbc);
    OffsetDateTime now = OffsetDateTime.parse("2026-09-22T07:20:00+07:00");
    Lot lot = new Lot();

    @BeforeEach void setup() {
        lot.setLotId("A"); lot.setCurrentCount(460);
        when(lots.findById("A")).thenReturn(Optional.of(lot));
        when(clock.read()).thenReturn(new ParkFlowClock.Reading("sim", now));
        List<BaselineService.Point> points = new ArrayList<>();
        for (int i = 0; i < 29; i++) {
            double count = i == 0 ? 20 : i < 5 ? 100 : i == 5 ? 424 : i == 6 ? 442 : 565;
            points.add(new BaselineService.Point(LocalTime.of(6, 0).plusMinutes(i * 15L), count));
        }
        when(baseline.baseline(eq("A"), any())).thenReturn(points);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq("A"), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(440L);
    }

    @Test void simulatedTimeAndUnroundedFactorMatchDocumentedExample() {
        var result = service.forecast("A", now.toLocalDate());
        assertEquals(now, result.generatedAt());
        assertEquals(440.0 / 410, result.k(), 1e-9);
        assertEquals(605, result.points().get(7).count());
        assertEquals(424, result.points().get(5).count());
        assertEquals(29, result.points().size());
    }

    @Test void correctionIsAnchoredAtCurrentCountAtExactSlot() {
        when(clock.read()).thenReturn(new ParkFlowClock.Reading("sim", now.withMinute(15)));
        assertEquals(460, service.forecast("A", now.toLocalDate()).points().get(5).count());
    }

    @Test void otherDatesAndOutsideWindowKeepBaselineWithoutLiveQuery() {
        assertEquals(1, service.forecast("A", now.toLocalDate().plusDays(1)).k());
        for (int hour : new int[]{5, 14}) {
            when(clock.read()).thenReturn(new ParkFlowClock.Reading("sim", now.withHour(hour)));
            assertEquals(565, service.forecast("A", now.toLocalDate()).points().get(7).count());
        }
        verifyNoInteractions(jdbc);
    }

    @Test void insufficientHistoryIsExplicit() {
        when(baseline.baseline(anyString(), any())).thenReturn(List.of());
        assertEquals("NO_HISTORY", assertThrows(ApiException.class,
                () -> service.forecast("A", now.toLocalDate())).code());
        verifyNoInteractions(jdbc);
    }

    @Test void factorThresholdClampsAndCorrectionFloorArePreserved() {
        assertEquals(1, ForecastMath.factor(100, 29));
        assertEquals(1, ForecastMath.factor(100, 0));
        assertEquals(1, ForecastMath.factor(100, -10));
        assertEquals(1.5, ForecastMath.factor(100, 30));
        assertEquals(0.7, ForecastMath.factor(-10, 30));
        assertEquals(1.2, ForecastMath.factor(36, 30));
        assertEquals(0, ForecastMath.corrected(20, 1.5, 0, 430));
        assertEquals(605, ForecastMath.corrected(460, 440.0 / 410, 565, 430));
    }
}