package com.parkflow.backend.approval;

import com.parkflow.backend.common.ParkFlowClock;
import com.parkflow.backend.plan.PlanHysteresis.*;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanApiTest {
    @Autowired MockMvc mvc;
    @Autowired PlanVersionService plans;
    @Autowired ObjectMapper mapper;
    @MockitoBean ParkFlowClock clock;
    LocalDate date=LocalDate.of(2041,10,3);
    @BeforeEach void setup() {
        when(clock.read()).thenReturn(new ParkFlowClock.Reading("sim",date.atTime(7,20).atOffset(ZoneOffset.ofHours(7))));
    }
    PlanVersionService.Version create(int peak,int shortfall) {
        var body=mapper.createObjectNode().put("shortfall",shortfall).put("reason","Quá tải");
        body.putObject("window").put("from","07:15").put("to","08:00");
        body.putArray("moves").addObject().put("from","A").put("to","B").put("vehicles",10).put("quota",10).put("used",0);
        body.putArray("before"); body.putArray("after"); body.putArray("excluded"); body.putArray("gateActions");
        body.putObject("envelope").put("maxMoveTotal",150).put("maxTargetPct",1).putArray("allowedDest").add("B");
        if(shortfall>0) body.putArray("shortfallOptions").addObject().put("code","ACCEPT_HIGHER").put("label","Chấp nhận mức cao hơn");
        return plans.create(date,body,new Snapshot(Map.of("A",new LotSnapshot(peak,500)),shortfall));
    }
    @Test void emptyStateReturnsNoPlan() throws Exception {
        mvc.perform(get("/api/plan/current")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NO_PLAN"));
    }
    @Test void approvesDraftAndRejectsDuplicateApproval() throws Exception {
        var v=create(565,0);
        mvc.perform(post("/api/plan/"+v.version()+"/approve").contentType("application/json").content("{\"approvedBy\":\"admin\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("approved"));
        mvc.perform(post("/api/plan/"+v.version()+"/approve").contentType("application/json").content("{\"approvedBy\":\"admin\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PLAN_STALE"));
    }
    @Test void shortfallRequiresOfferedOption() throws Exception {
        var v=create(605,10);
        mvc.perform(post("/api/plan/"+v.version()+"/approve").contentType("application/json").content("{\"approvedBy\":\"admin\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        mvc.perform(post("/api/plan/"+v.version()+"/approve").contentType("application/json").content("{\"approvedBy\":\"admin\",\"option\":\"ACCEPT_HIGHER\"}"))
            .andExpect(status().isOk());
    }
    @Test void pendingPlanDoesNotChangeActivePlanOrForecast() throws Exception {
        var first=create(565,0); plans.approve(first.version(),"admin",null);
        var next=create(600,10);
        mvc.perform(get("/api/plan/current")).andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(first.version()))
            .andExpect(jsonPath("$.pending.version").value(next.version()))
            .andExpect(jsonPath("$.pending.shortfall").value(10))
            .andExpect(jsonPath("$._alternatives").doesNotExist());
        assertEquals(555,plans.afterPlan(date,"A",LocalTime.of(7,30),565));
        assertEquals(575,plans.afterPlan(date,"B",LocalTime.of(7,30),565));
        assertEquals(565,plans.afterPlan(date,"A",LocalTime.of(8,15),565));
    }
}