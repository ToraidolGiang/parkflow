package com.parkflow.backend.quality;

/**
 * Cờ chất lượng dữ liệu của một bãi xe.
 *
 * <ul>
 *   <li>{@code ok} – số liệu bình thường, không phát hiện bất thường.</li>
 *   <li>{@code missing_out} – nhiều lượt vào thiếu lượt ra trong cửa sổ kiểm tra
 *       (raw_count âm sâu so với current_count – cho thấy cổng ra bỏ sót nhiều sự kiện).</li>
 *   <li>{@code implausible_count} – số xe hiện tại đang vượt sức chứa tối đa thiết kế
 *       (current_count &gt;= max_capacity).</li>
 *   <li>{@code signal_lost} – bãi im quá lâu trong khung giờ hoạt động
 *       (không nhận được bất kỳ sự kiện nào vượt {@code SIGNAL_LOST_MINUTES}).</li>
 * </ul>
 *
 * Một bãi có thể bị gắn nhiều cờ cùng lúc; cờ được sắp xếp theo thứ tự ưu tiên hiển thị:
 * {@code signal_lost} &gt; {@code missing_out} &gt; {@code implausible_count} &gt; {@code ok}.
 */
public enum QualityFlag {
    ok,
    missing_out,
    implausible_count,
    signal_lost;
}
