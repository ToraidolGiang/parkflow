package com.parkflow.backend.plan;

import com.parkflow.backend.approval.PlanVersionService;
import com.parkflow.backend.common.*;
import com.parkflow.backend.forecast.ForecastService;
import com.parkflow.backend.repository.LotRepository;
import com.parkflow.backend.domain.Lot;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanSchedulerTest {
    LotRepository lots=mock(LotRepository.class);
    ForecastService forecasts=mock(ForecastService.class);
    PlanVersionService versions=mock(PlanVersionService.class);
    ParkFlowClock clock=mock(ParkFlowClock.class);
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    PlanScheduler scheduler=new PlanScheduler(lots,forecasts,versions,clock,jdbc,new ObjectMapper());
    @Test void outsideOperatingWindowDoesNotPublish() {
        when(clock.read()).thenReturn(new ParkFlowClock.Reading("sim",OffsetDateTime.parse("2026-10-03T14:00:00+07:00")));
        scheduler.refresh(); verifyNoInteractions(lots,forecasts,versions,jdbc);
    }
    @Test void missingHistoryStopsWholePlanInsteadOfTreatingLotAsEmpty() {
        var now=OffsetDateTime.parse("2026-10-03T07:20:00+07:00");
        when(clock.read()).thenReturn(new ParkFlowClock.Reading("sim",now));
        Lot lot=new Lot(); lot.setLotId("A");
        when(lots.findAll()).thenReturn(List.of(lot));
        when(forecasts.forecast("A",now.toLocalDate())).thenThrow(new ApiException(HttpStatus.NOT_FOUND,"NO_HISTORY","Missing"));
        scheduler.refresh(); verifyNoInteractions(versions,jdbc);
    }
    @Test void runsAtSixtySecondFixedDelay() throws Exception {
        var annotation=PlanScheduler.class.getMethod("refresh").getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertEquals(60000,annotation.fixedDelay());
    }
}