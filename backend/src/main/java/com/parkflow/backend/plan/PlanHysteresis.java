package com.parkflow.backend.plan;

import java.util.Map;
import java.util.Objects;

/** So với bản kế hoạch được giữ lại, không so với từng lần đo nhỏ liên tiếp. */
public final class PlanHysteresis {
    private PlanHysteresis() {}

    public record LotSnapshot(int peak, int capacity) {
        public LotSnapshot {
            if (peak < 0 || capacity <= 0) throw new IllegalArgumentException("Invalid lot snapshot");
        }
        boolean overloaded() { return peak >= PlanConfig.OVER * capacity; }
    }

    public record Snapshot(Map<String, LotSnapshot> lots, int shortfall) {
        public Snapshot {
            lots = Map.copyOf(lots);
            if (shortfall < 0) throw new IllegalArgumentException("Negative shortfall");
        }
    }

    public static boolean shouldReplace(Snapshot previous, Snapshot candidate) {
        Objects.requireNonNull(candidate, "Candidate is required");
        if (previous == null) return true;
        if (previous.shortfall() != candidate.shortfall()
                || !previous.lots().keySet().equals(candidate.lots().keySet())) return true;
        for (var entry : candidate.lots().entrySet()) {
            LotSnapshot next = entry.getValue();
            LotSnapshot old = previous.lots().get(entry.getKey());
            if (next.capacity() != old.capacity() || next.overloaded() != old.overloaded()) return true;
            double threshold = Math.max(PlanConfig.HYST_ABS, PlanConfig.HYST_PCT * next.capacity());
            if (Math.abs((long) next.peak() - old.peak()) >= threshold) return true;
        }
        return false;
    }

    public static Snapshot retainOrReplace(Snapshot previous, Snapshot candidate) {
        return shouldReplace(previous, candidate) ? candidate : previous;
    }
}