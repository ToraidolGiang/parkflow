package com.parkflow.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parkflow.backend.approval.PlanVersionService;
import com.parkflow.backend.plan.PlanHysteresis;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Tỷ lệ làm theo gợi ý: xe thực tế vào bãi đích trong khung của kế hoạch chia cho số suất đã phát. Cần Postgres; dọn dữ liệu của chính test. */
@SpringBootTest
@AutoConfigureMockMvc
class ComplianceControllerTest {
    private static final LocalDate DATE = LocalDate.parse("2031-03-03");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlanVersionService plans;
    @Autowired ObjectMapper mapper;
    private long version;

    @BeforeEach void setup() {
        cleanup();
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate) VALUES ('CP1', 'Bãi nguồn', 100, 120, 'MAIN')");
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate) VALUES ('CP2', 'Bãi đích', 100, 120, 'MAIN')");
        jdbc.update("INSERT INTO plan_state (target_date) VALUES (?) ON CONFLICT DO NOTHING", DATE);
        ObjectNode body = mapper.createObjectNode();
        body.putObject("window").put("from", "07:00").put("to", "07:15");
        body.putArray("moves").addObject().put("from", "CP1").put("to", "CP2").put("vehicles", 10).put("quota", 10).put("used", 0);
        version = plans.create(DATE, body, new PlanHysteresis.Snapshot(Map.of(), 0)).version();
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM events WHERE lot_id IN ('CP1','CP2')");
        jdbc.update("DELETE FROM plan_state WHERE target_date = ?", DATE);
        jdbc.update("DELETE FROM plan_versions WHERE target_date = ?", DATE);
        jdbc.update("DELETE FROM lots WHERE lot_id IN ('CP1','CP2')");
    }

    private void arrivals(int count, String time) {
        for (int i = 0; i < count; i++)
            jdbc.update("INSERT INTO events (event_id, ts, lot_id, direction, vehicle_type, card_hash) VALUES (?, ?::timestamptz, 'CP2', 'in', 'car', ?)",
                    "cp-" + time + "-" + i, "2031-03-03T" + time + "+07:00", "h-" + time + i);
    }

    @Test void complianceIsArrivalsInTheWindowOverIssuedQuota() throws Exception {
        arrivals(6, "07:05:00");
        arrivals(5, "08:30:00");   // ngoài khung, không tính
        mvc.perform(get("/api/plan/" + version + "/compliance")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version)).andExpect(jsonPath("$.byMove[0].from").value("CP1"))
                .andExpect(jsonPath("$.byMove[0].to").value("CP2")).andExpect(jsonPath("$.byMove[0].issued").value(10))
                .andExpect(jsonPath("$.byMove[0].arrived").value(6)).andExpect(jsonPath("$.byMove[0].compliance").value(0.6))
                .andExpect(jsonPath("$.overall").value(0.6));
    }

    @Test void arrivalsAboveTheQuotaDoNotPushComplianceOverOneHundredPercent() throws Exception {
        arrivals(14, "07:10:00");
        mvc.perform(get("/api/plan/" + version + "/compliance")).andExpect(status().isOk())
                .andExpect(jsonPath("$.byMove[0].arrived").value(14)).andExpect(jsonPath("$.byMove[0].compliance").value(1.0))
                .andExpect(jsonPath("$.overall").value(1.0));
    }

    @Test void noArrivalsMeansZeroComplianceForAMoveWithQuota() throws Exception {
        mvc.perform(get("/api/plan/" + version + "/compliance")).andExpect(status().isOk())
                .andExpect(jsonPath("$.byMove[0].arrived").value(0)).andExpect(jsonPath("$.overall").value(0.0));
    }

    @Test void anUnknownVersionIs404() throws Exception {
        mvc.perform(get("/api/plan/99999999/compliance")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("PLAN_NOT_FOUND"));
    }
}
