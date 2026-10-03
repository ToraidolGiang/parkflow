package com.parkflow.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Kiểm tra sức khỏe: ok khi CSDL trả lời, 503 khi mất kết nối. Không cần Postgres. */
class HealthControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test void reportsOkWhenTheDatabaseAnswers() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        var response = new HealthController(jdbc).health();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("ok", response.getBody().get("status"));
        assertEquals("ok", response.getBody().get("db"));
    }

    @Test void reportsDegradedWithA503WhenTheDatabaseIsUnreachable() {
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new CannotGetJdbcConnectionException("không kết nối được"));
        var response = new HealthController(jdbc).health();
        assertEquals(503, response.getStatusCode().value());
        assertEquals("degraded", response.getBody().get("status"));
        assertEquals("down", response.getBody().get("db"));
    }
}
