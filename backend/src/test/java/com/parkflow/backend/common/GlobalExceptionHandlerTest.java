package com.parkflow.backend.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test void anApiExceptionKeepsItsStatusAndStableCode() {
        var response = handler.api(new ApiException(HttpStatus.NOT_FOUND, "UNKNOWN_LOT", "Không tìm thấy bãi"));
        assertEquals(404, response.getStatusCode().value());
        assertEquals("UNKNOWN_LOT", response.getBody().get("error"));
        assertEquals("Không tìm thấy bãi", response.getBody().get("message"));
    }

    @Test void badInputIsAlwaysABadRequestWithAFriendlyMessage() {
        var response = handler.badRequest(new IllegalArgumentException("chi tiết nội bộ"));
        assertEquals(400, response.getStatusCode().value());
        assertEquals("BAD_REQUEST", response.getBody().get("error"));
        assertEquals("Yêu cầu không hợp lệ.", response.getBody().get("message"), "không lộ thông điệp nội bộ");
    }

    @Test void anUnexpectedErrorNeverLeaksDetails() {
        var response = handler.unexpected(new RuntimeException("mật khẩu CSDL sai ở máy 10.0.0.5"));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", response.getBody().get("error"));
        assertEquals(false, response.getBody().get("message").toString().contains("10.0.0.5"));
    }
}
