package com.parkflow.backend.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Mọi lỗi trả về cùng một dạng {"error","message"} để giao diện xử lý thống nhất; không lộ chi tiết nội bộ. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> uploadTooLarge(Exception e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "TOO_LARGE", "File quá lớn (tối đa 20 MB).");
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        return body(e.status(), e.code(), e.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        String message = e instanceof MissingServletRequestParameterException missing
                ? "Thiếu tham số: " + missing.getParameterName()
                : "Yêu cầu không hợp lệ.";
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Hệ thống gặp lỗi, vui lòng thử lại.");
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
