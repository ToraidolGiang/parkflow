package com.parkflow.backend.forecast;

import java.time.LocalTime;
import java.util.Arrays;

/** Các phép tính thuần dùng chung cho dự báo khung 15 phút. */
public final class ForecastMath {
    public static final LocalTime START = LocalTime.of(6, 0);
    public static final LocalTime END = LocalTime.of(13, 0);
    public static final int SLOT_MINUTES = 15;
    public static final int SLOTS = 29;
    public static final double K_MIN = 0.7;
    public static final double K_MAX = 1.5;
    public static final int K_MIN_BASE = 30;

    private ForecastMath() {}

    public static double median(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Median requires at least one value");
        }
        double[] sorted = values.clone();
        for (double value : sorted) requireFinite(value);
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        return sorted.length % 2 == 0
                ? sorted[middle - 1] / 2 + sorted[middle] / 2 : sorted[middle];
    }

    public static double clampK(double k) {
        requireFinite(k);
        return Math.max(K_MIN, Math.min(K_MAX, k));
    }

    /** Ngưỡng 30 xe áp dụng cho net_forecast theo mục 3 của đặc tả. */
    public static double factor(double netActual, double netForecast) {
        requireFinite(netActual);
        requireFinite(netForecast);
        return netForecast < K_MIN_BASE ? 1 : clampK(netActual / netForecast);
    }

    public static int corrected(int current, double k, double baseSlot, double baseNow) {
        requireFinite(k);
        requireFinite(baseSlot);
        requireFinite(baseNow);
        return (int) Math.max(0, Math.round(current + k * (baseSlot - baseNow)));
    }

    /** Giờ giữa hai mốc thuộc khung bên trái; ngoài ca không có chỉ số hợp lệ. */
    public static int slotIndex(String time) {
        if (time == null || !time.matches("[0-9]{2}:[0-9]{2}")) {
            throw new IllegalArgumentException("Expected HH:mm");
        }
        return slotIndex(LocalTime.parse(time));
    }

    public static int slotIndex(LocalTime time) {
        requireInWindow(time);
        return (time.toSecondOfDay() - START.toSecondOfDay()) / (SLOT_MINUTES * 60);
    }

    public static double interpolate(double[] counts, LocalTime time) {
        if (counts == null || counts.length != SLOTS) {
            throw new IllegalArgumentException("Expected 29 forecast points");
        }
        for (double count : counts) requireFinite(count);
        int left = slotIndex(time);
        if (left == SLOTS - 1) return counts[left];
        double position = (time.toNanoOfDay() - START.toNanoOfDay())
                / (SLOT_MINUTES * 60_000_000_000.0);
        return counts[left] + (counts[left + 1] - counts[left]) * (position - left);
    }

    private static void requireInWindow(LocalTime time) {
        if (time == null || time.isBefore(START) || time.isAfter(END)) {
            throw new IllegalArgumentException("Time must be between 06:00 and 13:00");
        }
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Expected finite value");
    }
}
