package com.parkflow.backend.plan;

import static com.parkflow.backend.plan.PlanAllocator.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanAllocatorTest {
    static final List<GateInput> GATES = List.of(
            new GateInput("MAIN", "Cổng chính", 200, 0.95, true, LocalTime.of(6, 0)),
            new GateInput("EAST", "Cổng phía Đông", 200, 0.60, false, LocalTime.of(7, 0)));

    static List<LotInput> day(int a, int b, int c, int d) {
        return List.of(
                new LotInput("A", "Bãi A", 500, a, "MAIN", List.of("C", "B")),
                new LotInput("B", "Bãi B", 350, b, "EAST", List.of("C")),
                new LotInput("C", "Bãi C", 400, c, "EAST", List.of()),
                new LotInput("D", "Bãi D", 300, d, "MAIN", List.of()));
    }

    static Move move(Result r, String from, String to) {
        return r.moves().stream().filter(m -> m.from().equals(from) && m.to().equals(to)).findFirst().orElseThrow();
    }

    /** docs/03 ví dụ chuẩn 22/09 (TC-01). */
    @Test void standardDayMovesEightyFiveVehicles() {
        Result r = allocate(day(565, 311, 324, 198), GATES, Set.of(), 4);
        assertEquals(60, move(r, "A", "C").vehicles());
        assertEquals(25, move(r, "A", "B").vehicles());
        assertEquals(2, r.moves().size());
        assertEquals(0, r.shortfall());
        assertEquals(Map.of("A", 480, "B", 336, "C", 384, "D", 198), r.after());
        assertEquals("EAST", move(r, "A", "C").gate());
        assertEquals(60, move(r, "A", "C").quota());
        assertEquals(List.of(new Excluded("D", "Cổng chính đang quá tải")), r.excluded());
        assertEquals(Set.of("B", "C"), r.allowedDest());
    }

    /** docs/03 ví dụ đột biến (TC-05): thiếu 10 xe sau khi nâng lên TARGET_MAX. */
    @Test void spikeLeavesTenVehiclesShort() {
        Result r = allocate(day(605, 311, 324, 198), GATES, Set.of(), 4);
        assertEquals(76, move(r, "A", "C").vehicles());
        assertEquals(39, move(r, "A", "B").vehicles());
        assertEquals(10, r.shortfall());
        assertEquals(490, r.after().get("A"));
    }

    @Test void spikeAlternativeUsesLotDForTheRest() {
        Result r = allocate(day(605, 311, 324, 198), GATES, Set.of("D"), 4);
        assertEquals(0, r.shortfall());
        assertEquals(10, move(r, "A", "D").vehicles());
        assertEquals(76, move(r, "A", "C").vehicles());
        assertEquals(39, move(r, "A", "B").vehicles());
        assertEquals(480, r.after().get("A"));
        assertEquals(208, r.after().get("D"));
        assertTrue(r.excluded().isEmpty());
    }

    @Test void nothingMovesWhenNoLotIsOverloaded() {
        Result r = allocate(day(400, 300, 300, 200), GATES, Set.of(), 4);
        assertTrue(r.moves().isEmpty());
        assertTrue(r.excluded().isEmpty());
        assertEquals(0, r.shortfall());
    }

    @Test void everythingIsShortWhenNoDestinationHasRoom() {
        Result r = allocate(day(565, 340, 390, 290), GATES, Set.of(), 4);
        assertTrue(r.moves().isEmpty());
        assertEquals(85, r.shortfall());
    }

    /** Bãi không khai báo ưu tiên: xếp theo chỗ trống giảm dần, không dính danh sách mặc định cũ. */
    @Test void lotWithoutAlternatesFallsBackToLargestRoom() {
        List<LotInput> lots = List.of(
                new LotInput("A", "Bãi A", 500, 565, "MAIN", List.of()),
                new LotInput("B", "Bãi B", 350, 311, "EAST", List.of()),
                new LotInput("C", "Bãi C", 400, 324, "EAST", List.of()),
                new LotInput("D", "Bãi D", 300, 198, "MAIN", List.of()));
        Result r = allocate(lots, GATES, Set.of(), 4);
        assertEquals(60, move(r, "A", "C").vehicles());
        assertEquals(25, move(r, "A", "B").vehicles());
    }

    /** Bộ bãi khác hẳn (mã C1/C2, ưu tiên khác): thuật toán không giữ mã bãi hay ưu tiên nào cố định. */
    @Test void worksWithDifferentLotCodesAndPriorities() {
        List<GateInput> gates = List.of(
                new GateInput("G1", "Cổng Giải Phóng", 200, 0.5, false, LocalTime.of(6, 0)),
                new GateInput("G2", "Cổng Trần Đại Nghĩa", 200, 0.5, false, LocalTime.of(7, 0)));
        List<LotInput> lots = List.of(
                new LotInput("A", "Hầm 1", 1000, 1100, "G1", List.of("C2", "C1")),
                new LotInput("B", "Hầm 2", 1000, 600, "G2", List.of()),
                new LotInput("C1", "Nhà xe C1", 300, 100, "G2", List.of()),
                new LotInput("C2", "Nhà xe C2", 300, 100, "G2", List.of()));
        Result r = allocate(lots, gates, Set.of(), 4);
        // need = 1100 - floor(0,96 x 1000) = 140; ưu tiên C2 trước, C2 còn 288 - 100 = 188 chỗ nên nhận hết.
        assertEquals(1, r.moves().size());
        assertEquals(140, move(r, "A", "C2").vehicles());
        assertEquals(0, r.shortfall());
    }

    @Test void targetFollowsTheTriggerKeepingTheFourPointGap() {
        assertEquals(PlanConfig.TARGET, new LotInput("A", "A", 500, 0, "MAIN", List.of()).target());
        assertEquals(0.93, new LotInput("A", "A", 500, 0, "MAIN", List.of(), 0.97).target(), 1e-9);
        assertEquals(0.86, new LotInput("A", "A", 500, 0, "MAIN", List.of(), 0.90).target(), 1e-9);
    }

    @Test void loweredTriggerMakesALotBelowFullASourceAndDefaultTriggerDoesNot() {
        List<LotInput> normal = day(480, 311, 324, 198);   // A 96%: dưới 100% nên không phải nguồn
        assertTrue(allocate(normal, GATES, Set.of(), 4).moves().isEmpty());
        List<LotInput> withRule = List.of(
                new LotInput("A", "Bãi A", 500, 480, "MAIN", List.of("C", "B"), 0.90),
                normal.get(1), normal.get(2), normal.get(3));
        Result r = allocate(withRule, GATES, Set.of(), 4);
        assertEquals(50, move(r, "A", "C").vehicles());   // 480 − 86% × 500
        assertEquals(430, r.after().get("A"));
        assertEquals(0, r.shortfall());
    }
    @Test void gateCapacityCapsMovesAndReportsUnservedVehicles() {
        var gates = List.of(new GateInput("G", "G", 100, 0.8, false, LocalTime.of(6,0)));
        var lots = List.of(new LotInput("A","A",100,150,"G",List.of("B")),
                new LotInput("B","B",100,0,"G",List.of()));
        var result = allocate(lots,gates,Set.of(),1);
        assertEquals(20,result.moves().get(0).vehicles());
        assertEquals(34,result.shortfall());
        assertEquals(130,result.after().get("A"));
        assertEquals(20,result.after().get("B"));
    }

    @Test void multipleSourcesShareDestinationRoomWithoutOverfilling() {
        var gates = List.of(new GateInput("G","G",1000,0,false,LocalTime.of(6,0)));
        var lots = List.of(new LotInput("A","A",100,150,"G",List.of("C")),
                new LotInput("B","B",100,140,"G",List.of("C")),
                new LotInput("C","C",100,80,"G",List.of()));
        var result=allocate(lots,gates,Set.of(),1);
        assertEquals(100,result.after().get("C"));
        assertEquals(20,result.moves().stream().mapToInt(Move::vehicles).sum());
        assertEquals(78,result.shortfall());
    }

    @Test void saturatedGateCannotInventCapacity() {
        var gates=List.of(new GateInput("G","G",100,1,true,LocalTime.of(6,0)));
        var lots=List.of(new LotInput("A","A",100,150,"G",List.of("B")),
                new LotInput("B","B",100,0,"G",List.of()));
        var result=allocate(lots,gates,Set.of("B"),1);
        assertTrue(result.moves().isEmpty());
        assertEquals(54,result.shortfall());
    }
}