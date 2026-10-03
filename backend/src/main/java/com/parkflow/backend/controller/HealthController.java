package com.parkflow.backend.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** `GET /api/health`: `{status, db}` cho kiểm tra sau phát hành và theo dõi bên ngoài. Cơ sở dữ liệu hỏng thì 503. */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {
    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbUp;
        try {
            dbUp = Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1", Integer.class));
        } catch (RuntimeException e) {
            dbUp = false;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "ok" : "degraded");
        body.put("db", dbUp ? "ok" : "down");
        return ResponseEntity.status(dbUp ? 200 : 503).body(body);
    }
}
