package com.parkflow.backend.common;

import java.time.Instant;
import java.util.Optional;

/** Package sim supplies this bean; empty means the simulator is stopped. */
public interface SimulationTimeSource {
    Optional<Instant> currentTime();
}
