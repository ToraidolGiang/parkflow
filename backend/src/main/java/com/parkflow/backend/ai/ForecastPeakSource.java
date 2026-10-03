package com.parkflow.backend.ai;

import java.util.List;

/**
 * Đỉnh dự báo từng bãi trong khung đang xét, do bộ dự báo cung cấp (AI chỉ diễn giải số này, không tự tính).
 * Chưa có bộ dự báo thì không có bean nào cài đặt và `forecastPeaks` rỗng: trợ lý nói rõ chưa có dự báo.
 */
public interface ForecastPeakSource {
    record Peak(String lotId, String time, int count, double pct) {}

    List<Peak> peaks();
}
