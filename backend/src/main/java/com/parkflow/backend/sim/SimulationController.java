package com.parkflow.backend.sim;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sim")
public class SimulationController {
    private final SimulationService simulation;
    private final SimulationControlGuard guard;

    public SimulationController(SimulationService simulation, SimulationControlGuard guard) {
        this.simulation = simulation;
        this.guard = guard;
    }

    public record Start(LocalDate date, Integer speed) {}

    @PostMapping("/start")
    public Map<String, Object> start(@RequestHeader(value = "X-Api-Key", required = false) String key, @RequestBody Start body) {
        guard.check(key);
        return simulation.start(body.date(), body.speed() == null ? 30 : body.speed());
    }

    @PostMapping("/stop")
    public Map<String, Object> stop(@RequestHeader(value = "X-Api-Key", required = false) String key) {
        guard.check(key);
        return simulation.stop();
    }

    @GetMapping("/state")
    public Map<String, Object> state() { return simulation.state(); }
}
