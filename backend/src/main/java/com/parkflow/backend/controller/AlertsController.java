package com.parkflow.backend.controller;

import com.parkflow.backend.rules.AlertService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** `GET /api/alerts?limit=20`: cảnh báo do quy tắc kích hoạt, mới nhất trước (docs/thiet-ke/openapi.yaml). */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertsController {
    private final AlertService alerts;

    @GetMapping
    public List<AlertService.Alert> latest(@RequestParam(defaultValue = "20") int limit) {
        return alerts.latest(limit);
    }
}
