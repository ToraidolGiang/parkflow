package com.parkflow.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** `POST /api/events` đầu cuối: hợp lệ, trùng, sai, quá lô, khóa. Cần Postgres; dọn dữ liệu của chính test. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "parkflow.api-keys=khoa-thu-nghiem,khoa-du-phong")
class EventControllerTest {
    private static final String LOT = "EVC1";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void setup() {
        cleanup();
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate) VALUES (?, 'Bãi thử', 100, 120, 'MAIN')", LOT);
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM events WHERE lot_id = ?", LOT);
        jdbc.update("DELETE FROM lots WHERE lot_id = ?", LOT);
    }

    private static String event(String id, String lot, String direction) {
        return "{\"event_id\":\"" + id + "\",\"ts\":\"2026-10-03T07:12:44+07:00\",\"lot_id\":\"" + lot + "\",\"direction\":\"" + direction
                + "\",\"vehicle_type\":\"motorbike\",\"card_hash\":\"e91d4c" + id + "\"}";
    }

    private org.springframework.test.web.servlet.ResultActions send(String key, String body) throws Exception {
        var request = post("/api/events").contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) request = request.header("X-Api-Key", key);
        return mvc.perform(request);
    }

    @Test void oneEventIsAcceptedAndCountedOnTheLot() throws Exception {
        send("khoa-thu-nghiem", event("c1", LOT, "in"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(1)).andExpect(jsonPath("$.duplicate").value(0))
                .andExpect(jsonPath("$.rejected.length()").value(0));
        org.junit.jupiter.api.Assertions.assertEquals(1, jdbc.queryForObject("SELECT current_count FROM lots WHERE lot_id = ?", Integer.class, LOT));
    }

    @Test void aBatchMixesAcceptedDuplicateAndRejectedWithoutFailingTheWholeBatch() throws Exception {
        String batch = "[" + event("b1", LOT, "in") + "," + event("b1", LOT, "in") + "," + event("b2", "NOPE", "in") + ","
                + "{\"event_id\":\"b3\",\"ts\":\"hôm qua\",\"lot_id\":\"" + LOT + "\",\"direction\":\"in\",\"vehicle_type\":\"car\",\"card_hash\":\"h\"}]";
        send("khoa-du-phong", batch)
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(1)).andExpect(jsonPath("$.duplicate").value(1))
                .andExpect(jsonPath("$.rejected.length()").value(2))
                .andExpect(jsonPath("$.rejected[0].event_id").value("b2")).andExpect(jsonPath("$.rejected[0].reason").value("UNKNOWN_LOT"))
                .andExpect(jsonPath("$.rejected[1].reason").value("BAD_TS"));
    }

    @Test void moreThanFiveHundredEventsInOneCallIsRefusedWith413() throws Exception {
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 501; i++) many.append(i == 0 ? "" : ",").append(event("m" + i, LOT, "in"));
        send("khoa-thu-nghiem", many.append("]").toString())
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.error").value("BATCH_TOO_LARGE"));
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE lot_id = ?", Integer.class, LOT));
    }

    @Test void aMissingOrWrongApiKeyIsRefusedAndNothingIsWritten() throws Exception {
        send(null, event("k1", LOT, "in")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        send("sai-khoa", event("k2", LOT, "in")).andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE lot_id = ?", Integer.class, LOT));
    }

    @Test void aBodyThatIsNotJsonIsABadRequestInTheStandardErrorShape() throws Exception {
        send("khoa-thu-nghiem", "{không phải json").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
