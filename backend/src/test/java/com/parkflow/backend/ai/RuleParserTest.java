package com.parkflow.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.parkflow.backend.rules.RuleStore;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Câu tiếng Việt thành bản nháp quy tắc: bộ đoán theo từ khóa, dùng kết quả AI khi hợp lệ, bỏ khi sai. Không cần Postgres. */
class RuleParserTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RuleStore store = mock(RuleStore.class);
    private final AiService ai = mock(AiService.class);

    private RuleParser parser(boolean mock) {
        when(store.lotIds()).thenReturn(Set.of("A", "B", "C1", "C2"));
        return new RuleParser(ai, new AiMode(mock), store, mapper);
    }

    private JsonNode rule(JsonNode result) { return result.path("rule"); }

    @Test void aSentenceWithoutNumbersGetsTheDefaultsAndSaysSo() {
        var result = parser(true).parse("Báo cho tôi từ hôm trước nếu bãi nào sắp đầy");
        assertEquals("alert", rule(result).path("type").asString());
        assertEquals("ALL", rule(result).path("scope").asString());
        assertEquals("forecast_pct", rule(result).path("metric").asString());
        assertEquals(">=", rule(result).path("op").asString());
        assertEquals(90.0, rule(result).path("value").asDouble());
        assertEquals("06:30", rule(result).path("from").asString());
        assertEquals("08:00", rule(result).path("to").asString());
        assertEquals("mock", result.path("source").asString());
        assertTrue(result.path("explanation").asString().contains("đã dùng mặc định"));
    }

    @Test void lotThresholdAndTimeWindowAreTakenFromTheSentence() {
        var result = parser(true).parse("Cảnh báo khi bãi A dự báo đạt 95% từ 6h30 đến 8h");
        assertEquals("A", rule(result).path("scope").asString());
        assertEquals(95.0, rule(result).path("value").asDouble());
        assertEquals("06:30", rule(result).path("from").asString());
        assertEquals("08:00", rule(result).path("to").asString());
        assertTrue(result.path("explanation").asString().startsWith("Nếu mức lấp đầy dự báo của bãi A >= 95%"));
        assertTrue(!result.path("explanation").asString().contains("mặc định"));
    }

    @Test void aRoutingSentenceBecomesARoutingRuleOnForecastWithAThresholdInRange() {
        var result = parser(true).parse("Điều hướng xe khi dự báo bãi B từ 85% trở lên");
        assertEquals("routing", rule(result).path("type").asString());
        assertEquals("forecast_pct", rule(result).path("metric").asString());
        assertEquals(85.0, rule(result).path("value").asDouble());
        var clamped = parser(true).parse("Điều hướng xe khi dự báo 30%");
        assertEquals(50.0, rule(clamped).path("value").asDouble(), "ngưỡng điều hướng thấp nhất là 50");
    }

    @Test void inflowAndCurrentMetricsAreRecognised() {
        var inflow = parser(true).parse("Báo khi có trên 120 xe vào trong 15 phút");
        assertEquals("inflow_15m", rule(inflow).path("metric").asString());
        assertEquals(120.0, rule(inflow).path("value").asDouble());
        var current = parser(true).parse("Cảnh báo khi bãi C1 hiện tại đạt 100%");
        assertEquals("current_pct", rule(current).path("metric").asString());
        assertEquals("C1", rule(current).path("scope").asString());
    }

    @Test void anUnknownLotIsNotInventedAndFallsBackToAll() {
        assertEquals("ALL", rule(parser(true).parse("Báo khi bãi Z dự báo đạt 90%")).path("scope").asString());
    }

    @Test void theModelAnswerIsUsedWhenItPassesTheSameValidationAsSaving() throws Exception {
        var modelAnswer = (ObjectNode) mapper.readTree("""
                {"source":"groq","explanation":"Nếu bãi A dự báo từ 95% thì báo.","rule":{"name":"Báo A","type":"alert","scope":"A","from":"06:30","to":"08:00",
                 "metric":"forecast_pct","op":">=","value":95,"action":"Thông báo"}}""");
        when(ai.run(anyString(), anyString(), any(), any())).thenReturn(modelAnswer);
        var result = parser(false).parse("Báo bãi A dự báo 95%");
        assertEquals("groq", result.path("source").asString());
        assertEquals("A", rule(result).path("scope").asString());
    }

    @Test void aModelAnswerThatWouldNotBeSavableIsReplacedByTheKeywordGuess() throws Exception {
        var modelAnswer = (ObjectNode) mapper.readTree("""
                {"source":"groq","explanation":"x","rule":{"name":"Sai","type":"routing","scope":"A","from":"06:30","to":"08:00",
                 "metric":"current_pct","op":">=","value":95,"action":"Điều hướng"}}""");
        when(ai.run(anyString(), anyString(), any(), any())).thenReturn(modelAnswer);
        var result = parser(false).parse("Báo bãi A hiện tại 95%");
        assertEquals("mock", result.path("source").asString());
        assertEquals("current_pct", rule(result).path("metric").asString());
    }

    @Test void whenEveryProviderFailsTheKeywordGuessIsReturned() {
        when(ai.run(anyString(), anyString(), any(), any())).thenThrow(new AiUnavailable("lỗi", mapper.createObjectNode()));
        assertEquals("mock", parser(false).parse("Báo khi bãi B dự báo 92%").path("source").asString());
    }

    @Test void emptyOrOverlongTextIsRejected() {
        var parser = parser(true);
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("x".repeat(301)));
    }
}
