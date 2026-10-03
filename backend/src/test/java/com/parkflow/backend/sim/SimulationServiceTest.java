package com.parkflow.backend.sim;

import com.parkflow.backend.common.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationServiceTest {
    private MockedStatic<ReplayLog> replayLog;

    @BeforeEach void provideReplayEvents() {
        // Kiểm thử vòng đời dùng dữ liệu cố định, không phụ thuộc ngày của CSV demo.
        replayLog = mockStatic(ReplayLog.class);
        var entry = new ReplayLog.Event("evt_test_in", ReplayLog.DEMO_DATE.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                "A", "in", "motorbike", "test-card", ReplayLog.DEMO_DATE.atStartOfDay(java.time.ZoneOffset.UTC).plusHours(1).toInstant());
        var exit = new ReplayLog.Event("evt_test_out", entry.exit(), "A", "out", "motorbike", "test-card", null);
        replayLog.when(() -> ReplayLog.read(ReplayLog.DEMO_DATE)).thenReturn(List.of(entry, exit));
        replayLog.when(() -> ReplayLog.read(ReplayLog.DEMO_DATE.plusYears(1))).thenReturn(List.of());
    }

    @AfterEach void releaseReplayMock() { replayLog.close(); }

    @Test void spikeRequiresRunningSessionAndResetsOnRestart() {
        var service = new SimulationService(mock(EventSender.class));
        assertThrows(ApiException.class, () -> service.spike(null, 20));
        service.start(ReplayLog.DEMO_DATE, 30);
        assertEquals(new SimulationService.Spike(null, 20), service.spike(null, 20).get("spike"));
        assertEquals(new SimulationService.Spike("A", 20), service.spike("A", 20).get("spike"));
        assertThrows(IllegalArgumentException.class, () -> service.spike(null, 101));
        assertThrows(IllegalArgumentException.class, () -> service.spike("unknown-lot", 20));
        assertNull(service.spike(null, 0).get("spike"));
        service.spike(null, 20);
        service.stop();
        assertNull(service.start(ReplayLog.DEMO_DATE, 30).get("spike"));
    }
    @Test void startExposesSimulatedClockAndStopRestoresRealClock() {
        var service = new SimulationService(mock(EventSender.class));
        var state = service.start(ReplayLog.DEMO_DATE, 30);
        assertEquals(true, state.get("running"));
        assertEquals(ReplayLog.DEMO_DATE + "T06:00+07:00", state.get("simTime"));
        assertEquals(2, state.get("remaining"));
        assertTrue(service.currentTime().isPresent());
        assertThrows(ApiException.class, () -> service.start(ReplayLog.DEMO_DATE, 30));
        assertEquals(false, service.stop().get("running"));
        assertTrue(service.currentTime().isEmpty());
        assertEquals(true, service.start(ReplayLog.DEMO_DATE, 60).get("running"));
    }

    @Test void invalidStartDoesNotActivateClock() {
        var service = new SimulationService(mock(EventSender.class));
        assertThrows(IllegalArgumentException.class, () -> service.start(null, 30));
        assertThrows(IllegalArgumentException.class, () -> service.start(ReplayLog.DEMO_DATE, 0));
        assertThrows(ApiException.class, () -> service.start(ReplayLog.DEMO_DATE.plusYears(1), 30));
        assertTrue(service.currentTime().isEmpty());
    }

    @Test void stopPreventsSendingQueuedEvents() {
        var sender = mock(EventSender.class);
        var service = new SimulationService(sender);
        service.start(ReplayLog.DEMO_DATE, 3600);
        service.stop();
        service.tick();
        verifyNoInteractions(sender);
    }

    @Test void controlsRequireConfiguredKey() {
        assertThrows(ApiException.class, () -> new SimulationControlGuard("").check(null));
        var guard = new SimulationControlGuard("demo-key");
        assertThrows(ApiException.class, () -> guard.check("wrong"));
        assertDoesNotThrow(() -> guard.check("demo-key"));
    }
}
