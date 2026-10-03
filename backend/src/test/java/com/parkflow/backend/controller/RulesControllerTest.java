package com.parkflow.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** Quy tắc đầu cuối: lưu, liệt kê, sửa một phần, xóa và các lỗi 400/404. Cần Postgres; dọn quy tắc của chính test. */
@SpringBootTest
@AutoConfigureMockMvc
class RulesControllerTest {
    private static final String NAME_PREFIX = "[kiem-thu] ";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setup() {
        cleanup();
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate) VALUES ('RUL1', 'Bãi thử', 100, 120, 'MAIN') ON CONFLICT DO NOTHING");
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM rules WHERE name LIKE ?", NAME_PREFIX + "%");
        jdbc.update("DELETE FROM lots WHERE lot_id = 'RUL1'");
    }

    private String draft(String name, String type, String scope, String metric, String op, double value) {
        return "{\"name\":\"" + NAME_PREFIX + name + "\",\"type\":\"" + type + "\",\"scope\":\"" + scope + "\",\"from\":\"06:30\",\"to\":\"08:00\","
                + "\"metric\":\"" + metric + "\",\"op\":\"" + op + "\",\"value\":" + value + ",\"action\":\"Thông báo điều hành\"}";
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/rules").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createdId(String json) throws Exception {
        String body = create(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("id").asLong();
    }

    @Test void aValidRuleIsSavedListedAndDeleted() throws Exception {
        create(draft("một", "alert", "ALL", "forecast_pct", ">=", 90)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.createdAt").isString()).andExpect(jsonPath("$.metric").value("forecast_pct"));
        long id = createdId(draft("hai", "alert", "RUL1", "current_pct", ">", 95));
        mvc.perform(get("/api/rules")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id==" + id + ")].scope").value("RUL1"));
        mvc.perform(delete("/api/rules/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/rules/" + id)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("RULE_NOT_FOUND"));
    }

    @Test void invalidRulesAreRejectedWithBadRequest() throws Exception {
        create(draft("sai bãi", "alert", "NOPE", "forecast_pct", ">=", 90)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        create(draft("sai chỉ số", "alert", "ALL", "weather", ">=", 90)).andExpect(status().isBadRequest());
        create(draft("sai phép so", "alert", "ALL", "forecast_pct", "=", 90)).andExpect(status().isBadRequest());
        create(draft("âm", "alert", "ALL", "forecast_pct", ">=", -5)).andExpect(status().isBadRequest());
        create(draft("giờ ngược", "alert", "ALL", "forecast_pct", ">=", 90).replace("\"from\":\"06:30\",\"to\":\"08:00\"", "\"from\":\"09:00\",\"to\":\"08:00\""))
                .andExpect(status().isBadRequest());
    }

    @Test void aRoutingRuleNeedsForecastPercentageAboveFiftyUpToAHundred() throws Exception {
        create(draft("điều hướng đúng", "routing", "ALL", "forecast_pct", ">=", 85)).andExpect(status().isCreated());
        create(draft("sai chỉ số", "routing", "ALL", "current_pct", ">=", 85)).andExpect(status().isBadRequest());
        create(draft("sai phép so", "routing", "ALL", "forecast_pct", "<", 85)).andExpect(status().isBadRequest());
        create(draft("ngưỡng thấp", "routing", "ALL", "forecast_pct", ">=", 30)).andExpect(status().isBadRequest());
        create(draft("ngưỡng cao", "routing", "ALL", "forecast_pct", ">=", 130)).andExpect(status().isBadRequest());
    }

    @Test void patchChangesOnlyTheGivenFieldsAndKeepsTheOldRuleWhenInvalid() throws Exception {
        long id = createdId(draft("sửa", "alert", "ALL", "forecast_pct", ">=", 90));
        mvc.perform(patch("/api/rules/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.value").value(90.0));
        mvc.perform(patch("/api/rules/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"value\":95,\"to\":\"09:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value(95.0)).andExpect(jsonPath("$.to").value("09:00")).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(patch("/api/rules/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"value\":-1}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/rules")).andExpect(jsonPath("$[?(@.id==" + id + ")].value").value(95.0));
    }

    @Test void patchRejectsEmptyBodyUnknownFieldsAndMissingRules() throws Exception {
        long id = createdId(draft("ràng buộc", "alert", "ALL", "forecast_pct", ">=", 90));
        mvc.perform(patch("/api/rules/" + id).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/rules/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"id\":99}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/rules/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":\"có\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/rules/999999").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isNotFound());
    }
}
