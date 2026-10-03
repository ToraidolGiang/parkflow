package com.parkflow.backend.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Groq (giao diện giống OpenAI), nhà cung cấp ưu tiên số một. Khóa ở biến `GROQ_API_KEY`; để trống thì bị bỏ qua. */
@Component
@Order(1)
public class GroqClient extends HttpAiClient {
    private final String key;
    private final String url;
    private final String model;

    public GroqClient(ObjectMapper mapper, @Value("${parkflow.ai.groq.key:}") String key,
                      @Value("${parkflow.ai.groq.url:https://api.groq.com/openai/v1/chat/completions}") String url,
                      @Value("${parkflow.ai.groq.model:openai/gpt-oss-120b}") String model) {
        super(mapper);
        this.key = key;
        this.url = url;
        this.model = model;
    }

    @Override public String source() { return "groq"; }

    @Override public boolean enabled() { return !key.isBlank(); }

    @Override public String complete(String system, String user) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.putObject("response_format").put("type", "json_object");
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        JsonNode reply = post(url, "Authorization", "Bearer " + key, body);
        JsonNode text = reply.path("choices").path(0).path("message").path("content");
        if (!text.isString()) throw new AiUnavailableException("groq trả phản hồi không có nội dung");
        return text.asString();
    }
}
