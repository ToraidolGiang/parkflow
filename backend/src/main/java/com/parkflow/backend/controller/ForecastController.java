package com.parkflow.backend.controller;

import com.parkflow.backend.forecast.ForecastService;
import com.parkflow.backend.forecast.ForecastResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forecast")
public class ForecastController {
    private final ForecastService service;
    public ForecastController(ForecastService service) { this.service = service; }

    @GetMapping
    public ForecastResponse get(@RequestParam String lotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.getForecast(lotId, date);
    }
}