package com.parkflow.backend.plan;

import java.time.LocalTime;
import java.util.*;
import static com.parkflow.backend.plan.PlanConfig.*;

/**
 * Phân bổ xe từ bãi quá tải sang bãi còn chỗ (docs/03 mục 4). Hàm thuần: không DB, không đồng hồ.
 * Không có mã bãi hay danh sách ưu tiên nào viết cứng; mọi thứ đi vào qua {@link LotInput}/{@link GateInput}.
 */
public final class PlanAllocator {
    private PlanAllocator() {}

    /**
     * peak: đỉnh dự báo lại của bãi; gate: cổng mặc định; alternates: bãi thay thế theo thứ tự ưu tiên (có thể rỗng);
     * trigger: tỷ lệ sức chứa từ đó bãi bị coi là cần giảm tải (mặc định {@link PlanConfig#OVER}, quy tắc routing có thể hạ thấp).
     */
    public record LotInput(String id, String name, int capacity, int peak, String gate, List<String> alternates, double trigger) {
        public LotInput(String id, String name, int capacity, int peak, String gate, List<String> alternates) {
            this(id, name, capacity, peak, gate, alternates, OVER);
        }

        /** Mức đưa về sau điều hướng: cách ngưỡng kích hoạt đúng {@link PlanConfig#TRIGGER_GAP}, không vượt {@link PlanConfig#TARGET}. */
        public double target() {
            return trigger >= OVER ? TARGET : Math.min(TARGET, trigger - TRIGGER_GAP);
        }
    }
    public record GateInput(String id, String name, int capacityPer15, double utilization, boolean overloaded, LocalTime normalOpen) {}

    public record Excluded(String lotId, String reason) {}
    /** allowedDest: các bãi được phép làm đích mà không cần duyệt thêm (đủ điều kiện và cổng không quá tải). */
    public record Result(List<Move> moves, List<Excluded> excluded, int shortfall,
                         Map<String, Integer> after, Set<String> allowedDest, Set<String> blockedByGate) {}

    /**
     * @param forced       bãi bị loại vì cổng quá tải nhưng được dùng làm đích cuối cùng cho phần thiếu (phương án USE_LOT_x)
     * @param windowSlots  số khung 15 phút của cao điểm, để rải xe điều hướng khi ước tính tải cổng
     */
    public static Result allocate(List<LotInput> lots, List<GateInput> gates, Set<String> forced, int windowSlots) {
        if (windowSlots <= 0) throw new IllegalArgumentException("Window must contain at least one slot");
        Map<String, LotInput> byId = new LinkedHashMap<>();
        lots.forEach(l -> byId.put(l.id(), l));
        Map<String, GateInput> gateById = new LinkedHashMap<>();
        gates.forEach(g -> gateById.put(g.id(), g));

        Map<String, Integer> need = new LinkedHashMap<>();
        for (LotInput lot : lots) {
            int n = lot.peak() - cap(lot, lot.target());
            if (lot.peak() >= lot.trigger() * lot.capacity() && n > 0) need.put(lot.id(), n);
        }
        List<LotInput> sources = need.keySet().stream().map(byId::get)
                .sorted(Comparator.comparingInt((LotInput l) -> -need.get(l.id())).thenComparing(LotInput::id)).toList();

        Set<String> usable = new LinkedHashSet<>();
        Set<String> blocked = new LinkedHashSet<>();
        for (LotInput lot : lots) {
            if (need.containsKey(lot.id()) || lot.peak() >= lot.trigger() * lot.capacity() || lot.peak() >= WARN * lot.capacity()) continue;
            GateInput gate = gateById.get(lot.gate());
            if (gate == null) throw new IllegalArgumentException("Unknown destination gate");
            if (gate.overloaded() || gate.utilization() > 0.90) blocked.add(lot.id()); else usable.add(lot.id());
        }

        Map<String, Integer> inflow = new HashMap<>();
        Map<String, int[]> moved = new LinkedHashMap<>();   // "from->to" -> {vehicles}
        Map<String, Integer> remaining = new LinkedHashMap<>(need);

        distribute(sources, remaining, byId, usable, TARGET, inflow, moved);
        distribute(sources, remaining, byId, usable, TARGET_MAX, inflow, moved);
        Set<String> forcedUsed = new LinkedHashSet<>();
        for (String id : forced) if (blocked.contains(id)) forcedUsed.add(id);
        if (!forcedUsed.isEmpty()) distribute(sources, remaining, byId, forcedUsed, TARGET_MAX, inflow, moved);

        List<Move> moves = assignGates(moved, byId, gateById, windowSlots);
        Map<String, Integer> after = new LinkedHashMap<>();
        lots.forEach(l -> after.put(l.id(), l.peak()));
        for (Move m : moves) {
            after.merge(m.from(), -m.vehicles(), Integer::sum);
            after.merge(m.to(), m.vehicles(), Integer::sum);
        }
        List<Excluded> excluded = new ArrayList<>();
        if (!sources.isEmpty())
            for (String id : blocked)
                if (!forcedUsed.contains(id))
                    excluded.add(new Excluded(id, gateById.get(byId.get(id).gate()).name() + " đang quá tải"));
        int shortfall = need.values().stream().mapToInt(Integer::intValue).sum()
                - moves.stream().mapToInt(Move::vehicles).sum();
        return new Result(List.copyOf(moves), List.copyOf(excluded), shortfall, Collections.unmodifiableMap(after),
                Collections.unmodifiableSet(usable), Collections.unmodifiableSet(blocked));
    }

    /** Một lượt chia xe: theo ưu tiên của từng bãi nguồn, rồi các đích còn lại theo chỗ trống giảm dần. */
    private static void distribute(List<LotInput> sources, Map<String, Integer> remaining, Map<String, LotInput> byId,
                                   Set<String> destinations, double fraction, Map<String, Integer> inflow, Map<String, int[]> moved) {
        for (LotInput source : sources) {
            int left = remaining.get(source.id());
            if (left <= 0) continue;
            List<String> order = new ArrayList<>();
            for (String alt : source.alternates()) if (destinations.contains(alt) && !order.contains(alt)) order.add(alt);
            List<String> rest = new ArrayList<>(destinations);
            rest.removeAll(order);
            rest.sort(Comparator.comparingInt((String id) -> -room(byId.get(id), fraction, inflow)).thenComparing(id -> id));
            order.addAll(rest);
            for (String destId : order) {
                if (left <= 0) break;
                int take = Math.min(left, room(byId.get(destId), fraction, inflow));
                if (take <= 0) continue;
                moved.computeIfAbsent(source.id() + "->" + destId, k -> new int[1])[0] += take;
                inflow.merge(destId, take, Integer::sum);
                left -= take;
            }
            remaining.put(source.id(), left);
        }
    }

    /** 6b: chọn cổng có mức dùng thấp hơn; hòa thì dùng cổng mặc định của bãi đích. */
    private static List<Move> assignGates(Map<String, int[]> moved, Map<String, LotInput> byId,
                                          Map<String, GateInput> gateById, int windowSlots) {
        Map<String, Integer> extra = new HashMap<>();
        List<Move> moves = new ArrayList<>();
        for (var entry : moved.entrySet()) {
            String[] ends = entry.getKey().split("->");
            int vehicles = entry.getValue()[0];
            LotInput dest = byId.get(ends[1]);
            GateInput best = gateById.get(dest.gate());
            // Chỉ cổng mặc định có tuyến được xác nhận trong mô hình đầu vào hiện tại.
            if (best == null) throw new IllegalArgumentException("Unknown destination gate");
            int gateRoom = (int) Math.max(0, Math.floor((1 - best.utilization())
                    * best.capacityPer15() * windowSlots + 1e-6) - extra.getOrDefault(best.id(), 0));
            vehicles = Math.min(vehicles, gateRoom);
            if (vehicles == 0) continue;
            extra.merge(best.id(), vehicles, Integer::sum);
            moves.add(new Move(ends[0], ends[1], best.id(), vehicles, (int) Math.ceil(vehicles / COMPLIANCE_DEFAULT)));
        }
        return moves;
    }

    private static double usage(GateInput gate, Map<String, Integer> extra, int windowSlots) {
        return gate.utilization() + extra.getOrDefault(gate.id(), 0) / (double) Math.max(1, windowSlots) / gate.capacityPer15();
    }

    private static int room(LotInput lot, double fraction, Map<String, Integer> inflow) {
        return Math.max(0, cap(lot, fraction) - lot.peak() - inflow.getOrDefault(lot.id(), 0));
    }

    private static int cap(LotInput lot, double fraction) {
        return (int) Math.floor(fraction * lot.capacity() + 1e-6);
    }
}
