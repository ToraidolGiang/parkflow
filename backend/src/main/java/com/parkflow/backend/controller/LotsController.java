package com.parkflow.backend.controller;

import com.parkflow.backend.ingest.LotStatusService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** `GET /api/lots/status`: giao diện thăm dò 5 giây một lần. */
@RestController
@RequestMapping("/api/lots")
@RequiredArgsConstructor
public class LotsController {
    private final LotStatusService status;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return status.status();
    }
}
