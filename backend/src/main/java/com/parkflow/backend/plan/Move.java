package com.parkflow.backend.plan;

/** Xe sắp tới được điều hướng; quota là số suất gợi ý cần phát. */
public record Move(String from, String to, String gate, int vehicles, int quota) {
    public Move {
        if (from == null || from.isBlank() || to == null || to.isBlank()
                || from.equals(to) || gate == null || gate.isBlank()
                || vehicles <= 0 || quota < vehicles) {
            throw new IllegalArgumentException("Invalid routing move");
        }
    }

    public static Move withCompliance(String from, String to, String gate, int vehicles, double compliance) {
        if (!Double.isFinite(compliance) || compliance <= 0 || compliance > 1) {
            throw new IllegalArgumentException("Compliance must be in (0, 1]");
        }
        double quota = Math.ceil(vehicles / compliance);
        if (quota > Integer.MAX_VALUE) throw new IllegalArgumentException("Quota exceeds integer range");
        return new Move(from, to, gate, vehicles, (int) quota);
    }
}