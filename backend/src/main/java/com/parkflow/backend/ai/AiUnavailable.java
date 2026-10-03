package com.parkflow.backend.ai;

import tools.jackson.databind.node.ObjectNode;

/** Mọi nhà cung cấp AI đều lỗi: trả 503 `AI_UNAVAILABLE` kèm `fallback` là câu trả lời dữ liệu mẫu dựng từ số liệu thật. */
public class AiUnavailable extends RuntimeException {
    private final transient ObjectNode fallback;

    public AiUnavailable(String message, ObjectNode fallback) {
        super(message);
        this.fallback = fallback;
    }

    public ObjectNode fallback() { return fallback; }
}
