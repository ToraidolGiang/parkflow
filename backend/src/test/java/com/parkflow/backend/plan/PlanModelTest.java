package com.parkflow.backend.plan;

import java.time.LocalTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.parkflow.backend.plan.PlanConstraints.*;

class PlanModelTest {
    Map<String, LotLimit> lots = Map.of("A",new LotLimit(500,565,Set.of("MAIN")),
            "B",new LotLimit(350,311,Set.of("EAST")),"C",new LotLimit(400,324,Set.of("EAST")));
    Map<String, GateLimit> gates = Map.of("MAIN",new GateLimit(200,180),"EAST",new GateLimit(200,100));
    Plan plan(Move... moves) { return new Plan(List.of(moves),List.of(),0); }

    @Test void documentedMovesFitDestinationsAndSharedGate() {
        assertDoesNotThrow(() -> validate(plan(new Move("A","C","EAST",60,60),
                new Move("A","B","EAST",25,25)),lots,gates));
    }
    @Test void sumsMovesBeforeCheckingDestinationCapacity() {
        assertThrows(IllegalArgumentException.class, () -> validate(plan(
                new Move("A","B","EAST",25,25),new Move("C","B","EAST",15,15)),lots,gates));
    }
    @Test void sharedGateLimitAppliesAcrossDestinations() {
        assertThrows(IllegalArgumentException.class, () -> validate(plan(
                new Move("A","C","EAST",76,76),new Move("A","B","EAST",39,39)),lots,gates));
    }
    @Test void exactCapacityIsAllowedAndUnknownOrInaccessibleGateIsRejected() {
        assertDoesNotThrow(() -> validate(plan(new Move("A","B","EAST",39,39)),lots,gates));
        assertThrows(IllegalArgumentException.class, () -> validate(plan(new Move("A","B","MAIN",1,1)),lots,gates));
        assertThrows(IllegalArgumentException.class, () -> validate(plan(new Move("X","B","EAST",1,1)),lots,gates));
    }
    @Test void quotasUseCeilingAndValidateCompliance() {
        assertEquals(17,Move.withCompliance("A","B","EAST",10,0.6).quota());
        assertEquals(10,Move.withCompliance("A","B","EAST",10,1).quota());
        for(double invalid : new double[]{0,-1,1.1,Double.NaN}) {
            assertThrows(IllegalArgumentException.class, () -> Move.withCompliance("A","B","EAST",10,invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new Move("A","A","MAIN",1,1));
        assertThrows(IllegalArgumentException.class, () -> new Move("A","B","EAST",2,1));
    }
    @Test void planKeepsImmutableInputsAndNonnegativeShortfall() {
        var moves=new ArrayList<Move>();
        moves.add(new Move("A","B","EAST",1,1));
        var plan=new Plan(moves,List.of(),10);
        moves.clear();
        assertEquals(1,plan.moves().size());
        assertEquals(10,plan.shortfall());
        assertThrows(UnsupportedOperationException.class, () -> plan.moves().clear());
        assertThrows(IllegalArgumentException.class, () -> new Plan(List.of(),List.of(),-1));
    }
    @Test void gateActionsRequireValidTimeWindow() {
        assertDoesNotThrow(() -> new GateAction("EAST",LocalTime.of(6,45),LocalTime.of(7,0),LocalTime.of(8,0)));
        assertThrows(IllegalArgumentException.class, () -> new GateAction("EAST",LocalTime.of(8,0),LocalTime.of(7,0),LocalTime.of(8,0)));
    }
}