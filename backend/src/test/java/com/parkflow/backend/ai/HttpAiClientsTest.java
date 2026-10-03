package com.parkflow.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Hai nhà cung cấp gọi HTTP, thử với máy chủ giả trên cổng cục bộ: không gọi dịch vụ thật, không cần khóa. */
class HttpAiClientsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private String base;
    private final AtomicReference<String> seenBody = new AtomicReference<>();
    private final AtomicReference<String> seenAuth = new AtomicReference<>();
    private final AtomicReference<String> seenGeminiKey = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "";

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            seenGeminiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void groqSendsBearerKeyJsonModeAndReturnsTheMessageContent() {
        reply = "{\"choices\":[{\"message\":{\"content\":\"{\\\"answer\\\":\\\"ok\\\"}\"}}]}";
        var client = new GroqClient(mapper, "khoa-groq", base + "/chat", "mo-hinh-thu");
        assertEquals("{\"answer\":\"ok\"}", client.complete("hệ thống", "người dùng"));
        assertEquals("Bearer khoa-groq", seenAuth.get());
        var body = mapper.readTree(seenBody.get());
        assertEquals("mo-hinh-thu", body.path("model").asString());
        assertEquals("json_object", body.path("response_format").path("type").asString());
        assertEquals("hệ thống", body.path("messages").path(0).path("content").asString());
        assertEquals("người dùng", body.path("messages").path(1).path("content").asString());
    }

    @Test void geminiSendsTheKeyInAHeaderNeverInTheUrl() {
        reply = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"answer\\\":\\\"ok\\\"}\"}]}}]}";
        var client = new GeminiClient(mapper, "khoa-gemini", base + "/models", "mo-hinh-thu");
        assertEquals("{\"answer\":\"ok\"}", client.complete("hệ thống", "người dùng"));
        assertEquals("khoa-gemini", seenGeminiKey.get());
        var body = mapper.readTree(seenBody.get());
        assertEquals("hệ thống", body.path("systemInstruction").path("parts").path(0).path("text").asString());
        assertEquals("application/json", body.path("generationConfig").path("responseMimeType").asString());
    }

    @Test void aClientWithoutAKeyIsDisabled() {
        assertFalse(new GroqClient(mapper, "", base, "m").enabled());
        assertFalse(new GeminiClient(mapper, " ", base, "m").enabled());
        assertTrue(new GroqClient(mapper, "k", base, "m").enabled());
    }

    @Test void rateLimitAndServerErrorsBecomeUnavailableWithoutLeakingContent() {
        var client = new GroqClient(mapper, "k", base + "/chat", "m");
        for (int code : new int[] {429, 500, 503}) {
            status = code;
            reply = "{\"error\":\"bí mật trong phản hồi\"}";
            var error = assertThrows(AiClient.AiUnavailableException.class, () -> client.complete("s", "câu hỏi riêng tư"));
            assertTrue(error.getMessage().contains(String.valueOf(code)));
            assertFalse(error.getMessage().contains("câu hỏi riêng tư") || error.getMessage().contains("bí mật"));
        }
    }

    @Test void aReplyWithoutContentIsTreatedAsUnavailable() {
        reply = "{\"choices\":[]}";
        assertThrows(AiClient.AiUnavailableException.class, () -> new GroqClient(mapper, "k", base + "/chat", "m").complete("s", "u"));
        reply = "{\"candidates\":[]}";
        assertThrows(AiClient.AiUnavailableException.class, () -> new GeminiClient(mapper, "k", base + "/models", "m").complete("s", "u"));
    }

    @Test void anUnreachableServerIsUnavailable() {
        server.stop(0);
        assertThrows(AiClient.AiUnavailableException.class, () -> new GroqClient(mapper, "k", base + "/chat", "m").complete("s", "u"));
    }
}
