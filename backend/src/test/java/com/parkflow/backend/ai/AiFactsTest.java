package com.parkflow.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.parkflow.backend.ingest.LotStatusService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.ObjectMapper;

/** Số liệu cho AI: chỉ số tổng hợp, và không để AI đoán khi thiếu dự báo. Không cần Postgres. */
class AiFactsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LotStatusService lotStatus = mock(LotStatusService.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<ForecastPeakSource> forecast = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked") private final ObjectProvider<PlanSource> plan = mock(ObjectProvider.class);
    private final AiFacts facts = new AiFacts(lotStatus, forecast, plan, mapper);

    private void status() {
        when(lotStatus.status()).thenReturn(Map.of(
                "clock", Map.of("mode", "sim", "now", "2026-10-03T07:20:00+07:00"),
                "lots", List.of(Map.of("lotId", "A", "name", "Bãi A", "count", 460, "capacity", 500, "pct", 92.0, "dataFlag", "ok"))));
    }

    @Test void currentCountsComeFromTheLotStatusAndNeverContainCardData() {
        status();
        when(forecast.getIfAvailable()).thenReturn(null);
        when(plan.getIfAvailable()).thenReturn(null);
        var data = facts.build();
        assertEquals("2026-10-03T07:20:00+07:00", data.path("asOf").asText());
        assertEquals(92.0, data.path("lots").path(0).path("pct").asDouble());
        assertEquals("ok", data.path("lots").path(0).path("dataFlag").asText());
        String json = data.toString().toLowerCase();
        assertFalse(json.contains("card") || json.contains("hash"), "AI không nhận mã thẻ");
    }

    @Test void withoutAForecastTheNoteTellsAiNotToConcludeThereIsNoRisk() {
        status();
        when(forecast.getIfAvailable()).thenReturn(null);
        when(plan.getIfAvailable()).thenReturn(null);
        var data = facts.build();
        assertEquals(0, data.path("forecastPeaks").size());
        assertTrue(data.path("forecastNote").asText().contains("Chưa có dự báo"));
        assertTrue(data.path("plan").isNull());
    }

    @Test void forecastPeaksAndThePlanAreIncludedWhenTheirSourcesExist() {
        status();
        when(forecast.getIfAvailable()).thenReturn(() -> List.of(new ForecastPeakSource.Peak("A", "07:45", 605, 121.0)));
        when(plan.getIfAvailable()).thenReturn(() -> Optional.of(mapper.createObjectNode().put("version", 7).put("status", "pending_approval")));
        var data = facts.build();
        assertEquals(121.0, data.path("forecastPeaks").path(0).path("pct").asDouble());
        assertEquals("07:45", data.path("forecastPeaks").path(0).path("time").asText());
        assertTrue(data.path("forecastNote").isMissingNode());
        assertEquals(7, data.path("plan").path("version").asInt());
    }
}
