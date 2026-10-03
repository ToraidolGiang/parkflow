package com.parkflow.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Thứ tự nhà cung cấp, kiểm tra schema, hỏi lại một lần, dự phòng Mock. Không cần Postgres. */
class AiServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AiFacts facts = mock(AiFacts.class);
    private final AiMode mode = new AiMode(false);
    private final MockAi mockAi = new MockAi(mapper);

    private static final String GOOD = "{\"answer\":\"Bãi A 113%. Cần quản trị duyệt.\",\"facts\":[{\"label\":\"Bãi A\",\"value\":\"113%\"}],\"suggestions\":[\"Hỏi thêm\"]}";

    @BeforeEach void setup() {
        var data = mapper.createObjectNode().put("asOf", "2026-10-03T07:20:00+07:00");
        data.putArray("forecastPeaks").addObject().put("lotId", "A").put("time", "07:45").put("pct", 113.0);
        when(facts.build()).thenReturn(data);
    }

    private static class Fake implements AiClient {
        final String name; final boolean on; final java.util.function.Supplier<String> reply; final AtomicInteger calls = new AtomicInteger();
        Fake(String name, boolean on, java.util.function.Supplier<String> reply) { this.name = name; this.on = on; this.reply = reply; }
        public String source() { return name; }
        public boolean enabled() { return on; }
        public String complete(String system, String user) { calls.incrementAndGet(); return reply.get(); }
    }

    private AiService service(Fake... clients) { return new AiService(List.of(clients), mode, facts, mockAi, mapper); }

    @Test void theFirstEnabledProviderAnswersAndIsNamedAsTheSource() {
        var groq = new Fake("groq", true, () -> GOOD);
        var gemini = new Fake("gemini", true, () -> GOOD);
        var result = service(groq, gemini).chat("Sáng mai thế nào?", List.of());
        assertEquals("groq", result.path("source").asText());
        assertEquals(0, gemini.calls.get());
    }

    @Test void aProviderWithoutAKeyIsSkippedSilently() {
        var groq = new Fake("groq", false, () -> { throw new AssertionError("không được gọi"); });
        var gemini = new Fake("gemini", true, () -> GOOD);
        assertEquals("gemini", service(groq, gemini).chat("Câu hỏi", null).path("source").asText());
    }

    @Test void aFencedJsonReplyIsAccepted() {
        var groq = new Fake("groq", true, () -> "```json\n" + GOOD + "\n```");
        assertEquals("groq", service(groq).chat("Câu hỏi", null).path("source").asText());
    }

    @Test void aWrongSchemaIsAskedAgainOnceThenTheNextProviderIsUsed() {
        var groq = new Fake("groq", true, () -> "{\"noi_dung\":\"sai schema\"}");
        var gemini = new Fake("gemini", true, () -> GOOD);
        var result = service(groq, gemini).chat("Câu hỏi", null);
        assertEquals(2, groq.calls.get());
        assertEquals("gemini", result.path("source").asText());
    }

    @Test void anUnavailableProviderIsNotRetriedAndTheNextOneIsUsed() {
        var groq = new Fake("groq", true, () -> { throw new AiClient.AiUnavailableException("hết hạn mức"); });
        var gemini = new Fake("gemini", true, () -> GOOD);
        var result = service(groq, gemini).chat("Câu hỏi", null);
        assertEquals(1, groq.calls.get());
        assertEquals("gemini", result.path("source").asText());
    }

    @Test void whenEveryProviderFailsTheErrorCarriesTheMockAnswer() {
        var groq = new Fake("groq", true, () -> { throw new AiClient.AiUnavailableException("lỗi"); });
        var gemini = new Fake("gemini", true, () -> "không phải json");
        var error = assertThrows(AiUnavailable.class, () -> service(groq, gemini).chat("Câu hỏi", null));
        assertEquals("mock", error.fallback().path("source").asText());
        assertTrue(error.fallback().path("answer").asText().contains("Dữ liệu mẫu"));
    }

    @Test void theMockSwitchSkipsEveryProvider() {
        mode.setMock(true);
        var groq = new Fake("groq", true, () -> { throw new AssertionError("không được gọi"); });
        var result = service(groq).chat("Câu hỏi", null);
        assertEquals("mock", result.path("source").asText());
        assertEquals(0, groq.calls.get());
    }

    @Test void aReplyWithAPercentageNotInTheFactsIsReplacedByTheMockAnswer() {
        var groq = new Fake("groq", true, () -> "{\"answer\":\"Bãi A sẽ đạt 140%.\"}");
        var result = service(groq).chat("Câu hỏi", null);
        assertEquals("mock", result.path("source").asText());
        assertTrue(result.path("guarded").asBoolean());
        assertTrue(!result.path("answer").asText().contains("140"));
    }

    @Test void anEmptyOrOverlongQuestionIsRejected() {
        var service = service(new Fake("groq", true, () -> GOOD));
        assertThrows(IllegalArgumentException.class, () -> service.chat("  ", null));
        assertThrows(IllegalArgumentException.class, () -> service.chat("x".repeat(501), null));
    }

    @Test void theReplyAlwaysHasFactsAndSuggestionsArrays() {
        var groq = new Fake("groq", true, () -> "{\"answer\":\"Chỉ có lời.\"}");
        var result = service(groq).chat("Câu hỏi", null);
        assertTrue(result.path("facts").isArray());
        assertTrue(result.path("suggestions").isArray());
    }
}
