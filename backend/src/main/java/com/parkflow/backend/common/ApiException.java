package com.parkflow.backend.common;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ có mã ổn định: trả về dạng {"error": "MÃ", "message": "..."} đúng hợp đồng API. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }

    public String code() { return code; }
}
