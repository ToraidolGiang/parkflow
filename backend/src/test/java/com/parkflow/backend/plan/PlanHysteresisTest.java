package com.parkflow.backend.plan;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static com.parkflow.backend.plan.PlanHysteresis.*;
import static org.junit.jupiter.api.Assertions.*;

class PlanHysteresisTest {
    Snapshot snapshot(int peak,int capacity,int shortfall) {
        return new Snapshot(Map.of("A",new LotSnapshot(peak,capacity)),shortfall);
    }
    @Test void firstPlanIsAcceptedAndIdenticalPlanIsRetained() {
        var state=snapshot(565,500,0);
        assertTrue(shouldReplace(null,state));
        assertFalse(shouldReplace(state,state));
    }
    @Test void absoluteThresholdIsInclusiveInBothDirections() {
        var state=snapshot(565,500,0);
        assertFalse(shouldReplace(state,snapshot(579,500,0)));
        assertFalse(shouldReplace(state,snapshot(551,500,0)));
        assertTrue(shouldReplace(state,snapshot(580,500,0)));
        assertTrue(shouldReplace(state,snapshot(550,500,0)));
    }
    @Test void largeLotsMustAlsoMeetThreePercentThreshold() {
        var state=snapshot(1200,1000,0);
        assertFalse(shouldReplace(state,snapshot(1215,1000,0)));
        assertFalse(shouldReplace(state,snapshot(1229,1000,0)));
        assertTrue(shouldReplace(state,snapshot(1230,1000,0)));
    }
    @Test void crossingOverloadThresholdOverridesSmallDelta() {
        assertTrue(shouldReplace(snapshot(499,500,0),snapshot(500,500,0)));
        assertTrue(shouldReplace(snapshot(500,500,0),snapshot(499,500,0)));
    }
    @Test void anyShortfallChangeRequiresReplacement() {
        assertTrue(shouldReplace(snapshot(605,500,0),snapshot(605,500,10)));
        assertTrue(shouldReplace(snapshot(605,500,10),snapshot(605,500,9)));
        assertTrue(shouldReplace(snapshot(605,500,10),snapshot(605,500,0)));
    }
    @Test void smallRepeatedChangesAreComparedAgainstRetainedPlan() {
        var initial=snapshot(565,500,0);
        var retained=retainOrReplace(initial,snapshot(570,500,0));
        retained=retainOrReplace(retained,snapshot(575,500,0));
        assertSame(initial,retained);
        var next=snapshot(580,500,0);
        assertSame(next,retainOrReplace(retained,next));
    }
    @Test void changesInAnyLotOrConfiguredLotSetAreDetected() {
        var old=new Snapshot(Map.of("A",new LotSnapshot(565,500),"B",new LotSnapshot(300,350)),0);
        var changed=new Snapshot(Map.of("A",new LotSnapshot(565,500),"B",new LotSnapshot(315,350)),0);
        assertTrue(shouldReplace(old,changed));
        assertTrue(shouldReplace(old,snapshot(565,500,0)));
        assertTrue(shouldReplace(snapshot(565,500,0),snapshot(565,600,0)));
    }
    @Test void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,()->snapshot(-1,500,0));
        assertThrows(IllegalArgumentException.class,()->snapshot(0,0,0));
        assertThrows(IllegalArgumentException.class,()->snapshot(0,500,-1));
    }
}