package com.parkflow.backend.sim;

import java.util.List;

/** Chọn ổn định một tỷ lệ lượt vào; mỗi xe bổ sung luôn có lượt ra tương ứng. */
final class SpikeEvents {
    private SpikeEvents() {}

    static List<ReplayLog.Event> from(ReplayLog.Event event, String lotId, int pct) {
        if (event.id().startsWith("spk_") || !event.direction().equals("in")
                || event.exit() == null || !event.exit().isAfter(event.ts())
                || (lotId != null && !lotId.equals(event.lot()))
                || Integer.parseUnsignedInt(ReplayLog.hash(event.id()).substring(0, 6), 16) % 100 >= pct)
            return List.of();
        String card = ReplayLog.hash("spike|" + event.card());
        return List.of(
                new ReplayLog.Event("spk_" + ReplayLog.hash(event.id() + "in").substring(0, 40),
                        event.ts().plusSeconds(1), event.lot(), "in", event.vehicle(), card, event.exit().plusSeconds(1)),
                new ReplayLog.Event("spk_" + ReplayLog.hash(event.id() + "out").substring(0, 40),
                        event.exit().plusSeconds(1), event.lot(), "out", event.vehicle(), card, null));
    }
}
