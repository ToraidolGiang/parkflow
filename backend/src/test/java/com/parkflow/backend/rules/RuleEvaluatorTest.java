package com.parkflow.backend.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

/** Quy tắc kích hoạt cảnh báo: khung giờ, phạm vi, bật/tắt, một lần mỗi ngày, và GET /api/alerts. Cần Postgres; dọn dữ liệu của chính test. */
@SpringBootTest
@AutoConfigureMockMvc
class RuleEvaluatorTest {
    private static final String NAME = "[kiem-thu-canh-bao] ";
    private static final OffsetDateTime INSIDE = OffsetDateTime.parse("2030-02-04T07:15:00+07:00");
    @Autowired RuleEvaluator evaluator;
    @Autowired RuleStore rules;
    @Autowired AlertService alerts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach void setup() {
        cleanup();
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate, current_count, raw_count) VALUES ('EV1', 'Bãi đầy', 100, 150, 'MAIN', 95, 95)");
        jdbc.update("INSERT INTO lots (lot_id, name, capacity, max_capacity, gate, current_count, raw_count) VALUES ('EV2', 'Bãi vắng', 100, 150, 'MAIN', 10, 10)");
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM rules WHERE name LIKE ?", NAME + "%");   // cảnh báo đi theo (ON DELETE CASCADE)
        jdbc.update("DELETE FROM events WHERE lot_id IN ('EV1','EV2')");
        jdbc.update("DELETE FROM lots WHERE lot_id IN ('EV1','EV2')");
    }

    private RuleStore.Rule save(String type, String scope, String metric, String op, double value, String from, String to) {
        return rules.create(new RuleDraft(NAME + metric + scope, type, scope, from, to, metric, op, value, "Thông báo"));
    }

    @Test void aMatchingRuleInsideItsWindowFiresForTheLotThatMeetsTheCondition() {
        save("alert", "ALL", "current_pct", ">=", 90, "06:30", "08:00");
        assertEquals(1, evaluator.evaluate(INSIDE), "chỉ bãi EV1 (95%) thỏa, EV2 (10%) thì không");
        var fired = alerts.latest(100).stream().filter(a -> a.lotId().equals("EV1")).findFirst().orElseThrow();
        assertTrue(fired.message().contains("Bãi EV1 đang 95%"), fired.message());
        assertTrue(alerts.latest(100).stream().noneMatch(a -> a.lotId().equals("EV2")));
    }

    @Test void theSameRuleAndLotFireOnlyOncePerDay() {
        save("alert", "EV1", "current_pct", ">=", 90, "06:30", "08:00");
        assertEquals(1, evaluator.evaluate(INSIDE));
        assertEquals(0, evaluator.evaluate(INSIDE.plusMinutes(10)));
        assertEquals(1, evaluator.evaluate(INSIDE.plusDays(1)), "ngày hôm sau báo lại");
    }

    @Test void outsideTheWindowADisabledRuleAndARoutingRuleDoNotFire() {
        var outside = save("alert", "EV1", "current_pct", ">=", 90, "06:30", "08:00");
        assertEquals(0, evaluator.evaluate(OffsetDateTime.parse("2030-02-04T09:30:00+07:00")), "ngoài khung giờ");
        rules.patch(outside.id(), new tools.jackson.databind.ObjectMapper().readTree("{\"enabled\":false}"));
        assertEquals(0, evaluator.evaluate(INSIDE), "quy tắc tắt");
        save("routing", "EV1", "forecast_pct", ">=", 90, "06:30", "08:00");
        assertEquals(0, evaluator.evaluate(INSIDE), "quy tắc điều hướng không do bộ cảnh báo xử lý");
    }

    @Test void aRuleScopedToAnotherLotDoesNotFireForThisOne() {
        save("alert", "EV2", "current_pct", ">=", 90, "06:30", "08:00");
        assertEquals(0, evaluator.evaluate(INSIDE));
    }

    @Test void belowThresholdOperatorsWork() {
        save("alert", "EV2", "current_pct", "<", 20, "06:30", "08:00");
        assertEquals(1, evaluator.evaluate(INSIDE));
    }

    @Test void inflowRuleCountsEntriesInTheLastFifteenMinutes() {
        for (int i = 0; i < 4; i++)
            jdbc.update("INSERT INTO events (event_id, ts, lot_id, direction, vehicle_type, card_hash) VALUES (?, ?::timestamptz, 'EV2', 'in', 'car', ?)",
                    "ev-in-" + i, "2030-02-04T07:10:0" + i + "+07:00", "h" + i);
        save("alert", "EV2", "inflow_15m", ">=", 4, "06:30", "08:00");
        assertEquals(1, evaluator.evaluate(INSIDE));
        assertTrue(alerts.latest(100).stream().anyMatch(a -> a.message().contains("4 xe vào trong 15 phút")));
    }

    @Test void alertsEndpointListsNewestFirstAndClampsTheLimit() throws Exception {
        var rule = save("alert", "ALL", "current_pct", ">=", 5, "06:30", "08:00");
        evaluator.evaluate(INSIDE);
        evaluator.evaluate(INSIDE.plusDays(1));
        mvc.perform(get("/api/alerts?limit=2")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ruleId").value(rule.id())).andExpect(jsonPath("$[0].message").isString());
        mvc.perform(get("/api/alerts?limit=0")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        assertFalse(alerts.latest(100).isEmpty());
    }
}
