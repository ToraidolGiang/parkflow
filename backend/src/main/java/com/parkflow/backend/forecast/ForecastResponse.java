package com.parkflow.backend.forecast;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ForecastResponse(String lotId, LocalDate date, double k, OffsetDateTime generatedAt,
        Peak peak, List<Point> points, Thresholds thresholds) {
    public record Peak(String t, int count, double pct) {}
    public record Point(String t, Integer actual, int forecast, int afterPlan) {}
    public record Thresholds(int yellow, int over, int critical) {}
}