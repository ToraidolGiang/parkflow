package com.parkflow.backend.approval;

import com.parkflow.backend.common.*;
import com.parkflow.backend.plan.PlanHysteresis.*;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static com.parkflow.backend.approval.PlanVersionService.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class PlanVersionServiceTest {
    @Autowired PlanVersionService service;
    @Autowired ObjectMapper mapper;
    @MockitoBean ParkFlowClock clock;
    LocalDate date=LocalDate.of(2040,10,3);
    Snapshot snapshot(int peak) { return new Snapshot(Map.of("A",new LotSnapshot(peak,500)),0); }
    PlanVersionService.Version create(int peak) {
        when(clock.read()).thenReturn(new ParkFlowClock.Reading("sim",OffsetDateTime.parse("2026-10-03T07:20:00+07:00")));
        return service.create(date,mapper.createObjectNode().put("shortfall",0),snapshot(peak));
    }
    @Test void persistsVersionAndUsesSimulationClock() {
        var first=create(565);
        assertEquals(draft,first.status());
        assertEquals("2026-10-03T07:20+07:00",first.createdAt().toString());
        assertEquals(first.version(),service.latest(date).orElseThrow().version());
        assertTrue(service.applied(date).isEmpty());
        assertEquals(snapshot(565),service.find(first.version()).orElseThrow().snapshot());
    }
    @Test void hysteresisRetainsVersionUntilCumulativeDifferenceReachesThreshold() {
        var first=create(565);
        assertEquals(first.version(),create(575).version());
        assertTrue(create(580).version()>first.version());
    }
    @Test void appliesApprovedVersionAndKeepsItWhileNextDraftWaits() {
        var first=create(565);
        service.transition(first.version(),pending_approval,null);
        service.transition(first.version(),approved,"admin");
        var next=create(590);
        assertEquals(first.version(),service.applied(date).orElseThrow().version());
        assertEquals(next.version(),service.latest(date).orElseThrow().version());
        service.transition(next.version(),auto_applied,"system");
        assertEquals(next.version(),service.applied(date).orElseThrow().version());
    }
    @Test void staleAndAlreadyApprovedVersionsCannotTransition() {
        var first=create(565); var second=create(590);
        assertEquals("PLAN_STALE",assertThrows(ApiException.class,()->service.transition(first.version(),approved,"admin")).code());
        service.transition(second.version(),approved,"admin");
        assertThrows(ApiException.class,()->service.transition(second.version(),approved,"admin"));
    }
    @Test void emptyStateIsExplicitAndApproverIsRequired() {
        assertTrue(service.latest(date).isEmpty());
        assertTrue(service.applied(date).isEmpty());
        assertEquals("NO_PLAN",assertThrows(ApiException.class,()->service.transition(-1,approved,"admin")).code());
        var first=create(565);
        assertThrows(IllegalArgumentException.class,()->service.transition(first.version(),approved," "));
    }
    tools.jackson.databind.node.ObjectNode proposal(int vehicles,String destination) {
        var body=mapper.createObjectNode().put("shortfall",0);
        body.putArray("moves").addObject().put("from","A").put("to",destination).put("vehicles",vehicles);
        body.putArray("after").addObject().put("lotId",destination).put("pct",95);
        var envelope=body.putObject("envelope").put("maxMoveTotal",150).put("maxTargetPct",1);
        envelope.putArray("allowedDest").add("B");
        return body;
    }
    @Test void publishesPendingThenAutoAppliesOnlyInsideApprovedEnvelope() {
        when(clock.read()).thenReturn(new ParkFlowClock.Reading("sim",OffsetDateTime.parse("2026-10-03T07:20:00+07:00")));
        var first=service.publish(date,proposal(65,"B"),snapshot(565));
        assertEquals(pending_approval,first.status());
        service.approve(first.version(),"admin",null);
        var next=service.publish(date,proposal(90,"B"),snapshot(590));
        assertEquals(auto_applied,next.status());
        assertEquals(next.version(),service.applied(date).orElseThrow().version());
        assertEquals(next.version(),service.publish(date,proposal(91,"B"),snapshot(591)).version());
        var outside=service.publish(date,proposal(115,"C"),snapshot(615));
        assertEquals(pending_approval,outside.status());
        assertEquals(next.version(),service.applied(date).orElseThrow().version());
    }
    @Test void excessiveMoveTotalRequiresApprovalAndPreservesActivePlan() {
        when(clock.read()).thenReturn(new ParkFlowClock.Reading("sim",OffsetDateTime.parse("2026-10-03T07:20:00+07:00")));
        var first=service.publish(date,proposal(65,"B"),snapshot(565));
        service.approve(first.version(),"admin",null);
        var outside=service.publish(date,proposal(151,"B"),snapshot(651));
        assertEquals(pending_approval,outside.status());
        assertEquals(first.version(),service.applied(date).orElseThrow().version());
    }
}
