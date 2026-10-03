package com.parkflow.backend.quality;

import java.util.List;
import java.util.Map;

/**
 * Kết quả kiểm tra chất lượng của một bãi xe.
 *
 * @param lotId    mã bãi.
 * @param flags    danh sách cờ phát hiện; luôn có ít nhất một phần tử ({@code ok} nếu không có vấn đề).
 * @param details  chi tiết kỹ thuật tương ứng từng cờ (dành cho API response).
 */
public record LotQuality(String lotId, List<QualityFlag> flags, Map<String, Object> details) {

    /** Trả {@code true} nếu bãi không có cờ bất thường nào (chỉ có {@code ok}). */
    public boolean isOk() {
        return flags.size() == 1 && flags.get(0) == QualityFlag.ok;
    }

    /**
     * Cờ đại diện theo thứ tự ưu tiên: signal_lost > missing_out > implausible_count > ok.
     * Dùng để lấp trường {@code dataFlag} trong lots/status.
     */
    public String primaryFlag() {
        for (QualityFlag f : List.of(QualityFlag.signal_lost, QualityFlag.missing_out, QualityFlag.implausible_count)) {
            if (flags.contains(f)) return f.name();
        }
        return QualityFlag.ok.name();
    }
}
