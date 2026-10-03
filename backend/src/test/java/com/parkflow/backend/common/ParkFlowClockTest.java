package com.parkflow.backend.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ParkFlowClockTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<SimulationTimeSource> provider = mock(ObjectProvider.class);

    @Test void usesSimulatedTimeInVietnamTimeWhileTheSimulationRuns() {
        when(provider.getIfAvailable()).thenReturn(() -> Optional.of(Instant.parse("2026-09-22T00:30:00Z")));
        var reading = new ParkFlowClock(provider).read();
        assertEquals("sim", reading.mode());
        assertEquals(OffsetDateTime.parse("2026-09-22T07:30:00+07:00"), reading.now());
    }

    @Test void fallsBackToRealTimeWhenTheSimulationIsStopped() {
        when(provider.getIfAvailable()).thenReturn(() -> Optional.empty());
        var reading = new ParkFlowClock(provider).read();
        assertEquals("real", reading.mode());
        assertTrue(Duration.between(reading.now().toInstant(), Instant.now()).abs().getSeconds() < 5);
    }

    @Test void fallsBackToRealTimeWhenThereIsNoSimulatorAtAll() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertEquals("real", new ParkFlowClock(provider).read().mode());
    }
}
