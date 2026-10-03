package com.parkflow.backend.plan;

import java.util.List;

/** Kết quả tính toán thuần; phiên bản và trạng thái duyệt được bổ sung ở H9. */
public record Plan(List<Move> moves, List<GateAction> gateActions, int shortfall) {
    public Plan {
        moves = List.copyOf(moves);
        gateActions = List.copyOf(gateActions);
        if (shortfall < 0) throw new IllegalArgumentException("Shortfall cannot be negative");
    }
}