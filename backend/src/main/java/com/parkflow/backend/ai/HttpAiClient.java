package com.parkflow.backend.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Phần chung của hai nhà cung cấp gọi qua HTTP: hạn chờ, và đổi mọi lỗi mạng, quá hạn, 429, 5xx thành {@link AiUnavailableException}
 * để {@link AiService} chuyển sang nhà cung cấp kế tiếp. Không ghi nội dung câu hỏi hay câu trả lời vào nhật ký hay thông điệp lỗi.
 */
abstract class HttpAiClient implements AiClient {
    static final Duration TIMEOUT = Duration.ofSeconds(8);

    protected final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    HttpAiClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Gửi JSON, trả về cây JSON phản hồi. */
    protected JsonNode post(String url, String headerName, String headerValue, JsonNode body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                    .header("Content-Type", "application/json").header(headerName, headerValue)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new AiUnavailableException(source() + " trả mã " + response.statusCode());
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new AiUnavailableException(source() + " lỗi kết nối hoặc quá hạn chờ");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException(source() + " bị ngắt");
        }
    }
}
