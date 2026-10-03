package com.parkflow.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** `GET /api/lots/status` đúng hợp đồng: mức theo ngưỡng thiết kế, tổng, xe vào 15 phút gần nhất. Cần Postgres; dọn dữ liệu của chính test. */
@SpringBootTest
@AutoConfigureMockMvc
class LotsControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void setup() {
        cleanup();
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate, near_building, current_count, raw_count, last_event_at) VALUES ('ST1', 'Bãi một', 500, 600, 'MAIN', 'Nhà hành chính', 460, 460, now())");
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate, current_count, raw_count) VALUES ('ST2', 'Bãi hai', 100, 120, 'EAST', 100, 100)");
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate, current_count, raw_count) VALUES ('ST3', 'Bãi ba', 100, 150, 'EAST', 125, 125)");
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate, current_count, raw_count) VALUES ('ST4', 'Bãi bốn', 200, 240, 'EAST', 10, 10)");
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM events WHERE lot_id LIKE 'ST_'");
        jdbc.update("DELETE FROM lots WHERE lot_id LIKE 'ST_'");
    }

    private void event(String id, String direction, OffsetDateTime ts) {
        jdbc.update("INSERT INTO events (event_id, ts, lot_id, direction, vehicle_type, card_hash) VALUES (?, ?, 'ST1', ?, 'motorbike', ?)", id, ts, direction, "h" + id);
    }

    @Test void levelsFollowTheDesignCapacityThresholds() throws Exception {
        mvc.perform(get("/api/lots/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[?(@.lotId=='ST1')].pct").value(92.0))
                .andExpect(jsonPath("$.lots[?(@.lotId=='ST1')].level").value("yellow"))
                .andExpect(jsonPath("$.lots[?(@.lotId=='ST2')].level").value("red"))
                .andExpect(jsonPath("$.lots[?(@.lotId=='ST3')].level").value("critical"))
                .andExpect(jsonPath("$.lots[?(@.lotId=='ST4')].level").value("green"));
    }

    @Test void eachLotCarriesTheFieldsOfTheContract() throws Exception {
        mvc.perform(get("/api/lots/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[?(@.lotId=='ST1')].name").value("Bãi một"))
                .andExpect(jsonPath("$.lots[?(@.lotId=='ST1')].count").value(460))
                .andExpect(jsonPath("$.lots[?(@.lotId=='ST1')].capacity").value(500))
                .andExpect(jsonPath("$.lots[?(@.lotId=='ST1')].maxCapacity").value(600))
                .andExpect(jsonPath("$.lots[?(@.lotId=='ST1')].gate").value("MAIN"))
                .andExpect(jsonPath("$.lots[?(@.lotId=='ST1')].nearBuilding").value("Nhà hành chính"))
                .andExpect(jsonPath("$.lots[?(@.lotId=='ST1')].dataFlag").value("ok"))
                .andExpect(jsonPath("$.clock.mode").exists()).andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test void totalsCountVehiclesOverloadedLotsAndEntriesInTheLastFifteenMinutes() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        event("n1", "in", now.minusMinutes(5));
        event("n2", "in", now.minusMinutes(14));
        event("n3", "in", now.minusMinutes(40));   // quá cũ
        event("n4", "out", now.minusMinutes(3));   // ra không tính vào lượng xe vào
        var json = mvc.perform(get("/api/lots/status")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tree = new tools.jackson.databind.ObjectMapper().readTree(json);
        int sumOfOurLots = 460 + 100 + 125 + 10;
        assertEquals(true, tree.path("totals").path("count").asInt() >= sumOfOurLots);
        assertEquals(true, tree.path("totals").path("overloadedLots").asInt() >= 2);   // ST2 (100%) và ST3 (125%)
        assertEquals(2, tree.path("totals").path("inflowLast15").asInt());
    }
}
