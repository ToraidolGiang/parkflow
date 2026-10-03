package com.parkflow.backend.controller;

import com.parkflow.backend.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** `GET /api/config`: cấu hình trường (chỉ đọc): bãi, cổng, tòa nhà, ca học, tuyến chỉ đường, ngưỡng (docs/thiet-ke/04-api.md mục 7). */
@RestController
@RequestMapping("/api/config")
public class ConfigController {
    private final JsonNode config;

    public ConfigController(ObjectMapper mapper) {
        this.config = load(mapper);
    }

    @GetMapping
    public JsonNode config() {
        if (config == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CONFIG_UNAVAILABLE", "Chưa có tệp cấu hình trường.");
        return config;
    }

    /** Thiếu hoặc hỏng tệp thì không làm hỏng khởi động cả ứng dụng; lỗi rõ ràng khi có người gọi. */
    private static JsonNode load(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource("simulator/campus_config.json").getInputStream()) {
            return mapper.readTree(in);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
