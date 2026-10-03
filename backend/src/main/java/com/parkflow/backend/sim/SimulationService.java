package com.parkflow.backend.sim;

import com.parkflow.backend.common.ApiException;
import com.parkflow.backend.common.ParkFlowClock;
import com.parkflow.backend.common.SimulationTimeSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Phát lại log đóng gói theo đồng hồ giả lập qua đường nhập sự kiện chuẩn. */
@Service
public class SimulationService implements SimulationTimeSource {
    private final EventSender sender;
    private final PriorityQueue<ReplayLog.Event> queue = new PriorityQueue<>(Comparator.comparing(ReplayLog.Event::ts).thenComparing(ReplayLog.Event::id));
    private final AtomicBoolean ticking = new AtomicBoolean();
    private volatile boolean running;
    private volatile Instant now;
    private Instant start;
    private long began;
    private int speed = 30, sent, accepted, duplicate;
    private String error;
    private Spike spike;
    public record Spike(String lotId, int pct) {}

    public SimulationService(EventSender sender) { this.sender = sender; }

    public synchronized Map<String, Object> start(LocalDate date, int speed) {
        if (running || ticking.get()) throw new ApiException(HttpStatus.CONFLICT, "SIM_RUNNING", "Dừng lần phát hiện tại trước khi bắt đầu lại.");
        if (date == null || speed < 1 || speed > 3600) throw new IllegalArgumentException("Invalid date/speed");
        var events = ReplayLog.read(date);
        if (events.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "NO_SIM_DATA", "Không có log cho ngày chọn.");
        queue.clear();
        queue.addAll(events);
        this.speed = speed;
        start = date.atTime(6, 0).atZone(ParkFlowClock.ZONE).toInstant();
        now = start;
        began = System.nanoTime();
        sent = accepted = duplicate = 0;
        error = null;
        spike = null;
        running = true;
        return state();
    }

    public synchronized Map<String, Object> stop() { running = false; return state(); }

    public synchronized Map<String, Object> spike(String lotId, int pct) {
        if (pct < 0 || pct > 100) throw new IllegalArgumentException("pct must be 0..100");
        if (!running) throw new ApiException(HttpStatus.CONFLICT, "SIM_NOT_RUNNING", "Bắt đầu giả lập trước khi tăng đột biến.");
        if (lotId != null && ReplayLog.read(start.atZone(ParkFlowClock.ZONE).toLocalDate()).stream().noneMatch(event -> event.lot().equals(lotId)))
            throw new IllegalArgumentException("Unknown lotId");
        spike = pct == 0 ? null : new Spike(lotId, pct);
        return state();
    }

    public synchronized Map<String, Object> state() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("running", running);
        result.put("simTime", now == null ? null : now.atZone(ParkFlowClock.ZONE).toOffsetDateTime().toString());
        result.put("speed", speed);
        result.put("spike", spike);
        result.put("sent", sent);
        result.put("accepted", accepted);
        result.put("duplicate", duplicate);
        result.put("remaining", queue.size());
        result.put("error", error);
        return result;
    }

    @Override public Optional<Instant> currentTime() { return running ? Optional.ofNullable(now) : Optional.empty(); }

    @Scheduled(fixedDelay = 100)
    public void tick() {
        if (!ticking.compareAndSet(false, true)) return;
        try {
            for (int n = 0; n < 100; n++) {
                ReplayLog.Event event;
                synchronized (this) {
                    if (!running) return;
                    if (queue.isEmpty()) { running = false; return; }
                    Instant target = start.plusMillis((System.nanoTime() - began) / 1_000_000 * speed);
                    if (queue.peek().ts().isAfter(target)) { now = target; return; }
                    event = queue.peek();
                    now = event.ts();
                }
                // Không giữ khóa khi gửi HTTP: dừng và đọc đồng hồ vẫn phản hồi ngay.
                int added = sender.send(event);
                synchronized (this) {
                    accepted += added;
                    duplicate += 1 - added;
                    sent++;
                    queue.remove();
                    if (spike != null) queue.addAll(SpikeEvents.from(event, spike.lotId(), spike.pct()));
                    if (queue.isEmpty()) running = false;
                }
            }
        } catch (RuntimeException ex) {
            synchronized (this) { error = ex.getMessage(); running = false; }
        } finally { ticking.set(false); }
    }
}
