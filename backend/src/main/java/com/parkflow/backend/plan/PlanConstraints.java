package com.parkflow.backend.plan;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Kiểm tra kế hoạch cho một khung 15 phút; tải cổng là số xe đã có trước điều hướng. */
public final class PlanConstraints {
    private PlanConstraints() {}

    public record LotLimit(int capacity, int peak, Set<String> gates) {
        public LotLimit {
            if (capacity <= 0 || peak < 0) throw new IllegalArgumentException("Invalid lot limits");
            gates = Set.copyOf(gates);
            if (gates.isEmpty()) throw new IllegalArgumentException("Lot needs an accessible gate");
        }
    }
    public record GateLimit(int capacityPer15, int inflow) {
        public GateLimit {
            if (capacityPer15 <= 0 || inflow < 0) throw new IllegalArgumentException("Invalid gate limits");
        }
    }

    public static void validate(Plan plan, Map<String, LotLimit> lots, Map<String, GateLimit> gates) {
        Map<String, Long> incoming = new HashMap<>(), outgoing = new HashMap<>(), gateLoad = new HashMap<>();
        for (Move move : plan.moves()) {
            if (!lots.containsKey(move.from()) || !lots.containsKey(move.to()) || !gates.containsKey(move.gate())) {
                throw new IllegalArgumentException("Unknown lot or gate");
            }
            if (!lots.get(move.to()).gates().contains(move.gate())) {
                throw new IllegalArgumentException("Gate cannot reach destination");
            }
            incoming.merge(move.to(), (long) move.vehicles(), Long::sum);
            outgoing.merge(move.from(), (long) move.vehicles(), Long::sum);
            gateLoad.merge(move.gate(), (long) move.vehicles(), Long::sum);
        }
        for (var entry : incoming.entrySet()) {
            LotLimit lot = lots.get(entry.getKey());
            if (lot.peak() + entry.getValue() > lot.capacity()) {
                throw new IllegalArgumentException("Destination capacity exceeded");
            }
        }
        for (var entry : outgoing.entrySet()) {
            if (entry.getValue() > lots.get(entry.getKey()).peak()) {
                throw new IllegalArgumentException("Cannot divert more vehicles than forecast");
            }
        }
        for (var entry : gateLoad.entrySet()) {
            GateLimit gate = gates.get(entry.getKey());
            if (gate.inflow() + entry.getValue() > gate.capacityPer15()) {
                throw new IllegalArgumentException("Gate capacity exceeded");
            }
        }
        for (GateAction action : plan.gateActions()) {
            if (!gates.containsKey(action.gate())) throw new IllegalArgumentException("Unknown action gate");
        }
    }
}