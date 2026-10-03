package com.parkflow.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** `GET /api/gates/status`: xe vào cổng ước lượng theo tỷ lệ cổng của bãi, mức tải và hành động. Cần Postgres; dọn dữ liệu của chính test. */
@SpringBootTest
@AutoConfigureMockMvc
class GatesControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void setup() {
        cleanup();
        jdbc.update("INSERT INTO forecast_gates (gate, name, capacity_per_15, normal_open) VALUES ('GT9', 'Cổng thử', 20, '06:00')");
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate) VALUES ('GL1', 'Bãi cổng một', 100, 120, 'GT9')");
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate) VALUES ('GL2', 'Bãi cổng hai', 100, 120, 'GT9')");
        jdbc.update("INSERT INTO forecast_gate_shares (lot_id, gate, share) VALUES ('GL1', 'GT9', 1.0)");
        jdbc.update("INSERT INTO forecast_gate_shares (lot_id, gate, share) VALUES ('GL2', 'GT9', 0.5)");
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM events WHERE lot_id IN ('GL1','GL2')");
        jdbc.update("DELETE FROM forecast_gate_shares WHERE gate = 'GT9'");
        jdbc.update("DELETE FROM lots WHERE lot_id IN ('GL1','GL2')");
        jdbc.update("DELETE FROM forecast_gates WHERE gate = 'GT9'");
    }

    private void entries(String lot, int count) {
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < count; i++)
            jdbc.update("INSERT INTO events (event_id, ts, lot_id, direction, vehicle_type, card_hash) VALUES (?, ?, ?, 'in', 'car', ?)",
                    "gt-" + lot + "-" + i, now.minusMinutes(2), lot, "h-" + lot + i);
    }

    @Test void inflowIsTheSumOfEachLotEntriesTimesItsGateShareWithUtilizationAndLevel() throws Exception {
        entries("GL1", 10);   // 10 × 1,0
        entries("GL2", 12);   // 12 × 0,5 = 6   → 16 xe trên năng lực 20 = 0,8 (vàng)
        mvc.perform(get("/api/gates/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.clock.mode").exists())
                .andExpect(jsonPath("$.gates[?(@.gate=='GT9')].inflowLast15").value(16))
                .andExpect(jsonPath("$.gates[?(@.gate=='GT9')].capacityPer15").value(20))
                .andExpect(jsonPath("$.gates[?(@.gate=='GT9')].utilization").value(0.8))
                .andExpect(jsonPath("$.gates[?(@.gate=='GT9')].level").value("yellow"))
                .andExpect(jsonPath("$.gates[?(@.gate=='GT9')].source").value("estimated"));
    }

    @Test void aGateWithNoTrafficIsGreenWithNoPlanActionAndNoInventedForecast() throws Exception {
        mvc.perform(get("/api/gates/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.gates[?(@.gate=='GT9')].inflowLast15").value(0))
                .andExpect(jsonPath("$.gates[?(@.gate=='GT9')].level").value("green"))
                .andExpect(jsonPath("$.gates[?(@.gate=='GT9')].expectedIn.length()").value(0))
                .andExpect(jsonPath("$.gates[?(@.gate=='GT9')].action[0]").doesNotExist());
    }
}
