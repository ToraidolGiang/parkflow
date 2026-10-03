package com.parkflow.backend.common;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Kiểm khóa của nhà cung cấp gửi sự kiện (header `X-Api-Key`). Cấu hình `parkflow.api-keys` là danh sách khóa ngăn bằng dấu phẩy;
 * để trống thì không chặn (chạy dev trên máy). Trên máy chủ thật luôn đặt `EVENTS_API_KEY`.
 */
@Component
public class ApiKeyGuard {
    private final List<String> keys;

    public ApiKeyGuard(@Value("${parkflow.api-keys:}") String configured) {
        this.keys = Arrays.stream(configured.split(",")).map(String::trim).filter(key -> !key.isEmpty()).toList();
    }

    public void check(String provided) {
        if (keys.isEmpty()) return;
        boolean ok = provided != null && keys.stream().anyMatch(key -> constantTimeEquals(key, provided));
        if (!ok) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Thiếu hoặc sai khóa X-Api-Key.");
    }

    /** So sánh không phụ thuộc vị trí ký tự sai đầu tiên (chống dò khóa theo thời gian phản hồi). */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
