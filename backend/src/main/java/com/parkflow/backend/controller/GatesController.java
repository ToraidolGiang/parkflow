package com.parkflow.backend.controller;

import com.parkflow.backend.ingest.GateStatusService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** `GET /api/gates/status`: màn Bảo vệ, tình hình từng cổng. */
@RestController
@RequestMapping("/api/gates")
@RequiredArgsConstructor
public class GatesController {
    private final GateStatusService gates;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return gates.status();
    }
}
