package com.parkflow.backend.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Gemini, nhà cung cấp dự phòng. Khóa ở biến `GEMINI_API_KEY` và gửi qua header (không đặt khóa trong địa chỉ URL). */
@Component
@Order(2)
public class GeminiClient extends HttpAiClient {
    private final String key;
    private final String baseUrl;
    private final String model;

    public GeminiClient(ObjectMapper mapper, @Value("${parkflow.ai.gemini.key:}") String key,
                        @Value("${parkflow.ai.gemini.url:https://generativelanguage.googleapis.com/v1beta/models}") String baseUrl,
                        @Value("${parkflow.ai.gemini.model:gemini-3.1-flash-lite}") String model) {
        super(mapper);
        this.key = key;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override public String source() { return "gemini"; }

    @Override public boolean enabled() { return !key.isBlank(); }

    @Override public String complete(String system, String user) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        body.putArray("contents").addObject().put("role", "user").putArray("parts").addObject().put("text", user);
        body.putObject("generationConfig").put("responseMimeType", "application/json").put("temperature", 0.2);
        JsonNode reply = post(baseUrl + "/" + model + ":generateContent", "x-goog-api-key", key, body);
        JsonNode text = reply.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (!text.isString()) throw new AiUnavailableException("gemini trả phản hồi không có nội dung");
        return text.asString();
    }
}
