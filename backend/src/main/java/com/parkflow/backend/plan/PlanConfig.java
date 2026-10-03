package com.parkflow.backend.plan;

/** Tham số thuật toán phân luồng, theo docs/03 mục 0. */
public final class PlanConfig {
    private PlanConfig() {}
    /** Mức lấp đầy mục tiêu sau phân luồng (giữ biên an toàn). */
    public static final double TARGET = 0.96;
    /** Mức tối đa cho phép khi thiếu chỗ. */
    public static final double TARGET_MAX = 1.00;
    /** Bãi đích chỉ được chọn khi dự báo dưới ngưỡng này. */
    public static final double WARN = 0.90;
    /** Ngưỡng quá tải. */
    public static final double OVER = 1.00;
    /** Khoảng cách giữa ngưỡng kích hoạt và mức đưa về (mặc định 100% xuống 96%); quy tắc routing giữ nguyên khoảng này. */
    public static final double TRIGGER_GAP = OVER - TARGET;
    /** Quy tắc routing chỉ được hạ ngưỡng kích hoạt, và không thấp hơn mức này (tránh điều hướng khi bãi còn rất trống). */
    public static final double MIN_ROUTING_TRIGGER = 0.50;
    /** Chống nhảy kế hoạch: lệch tuyệt đối (xe) hoặc theo tỷ lệ sức chứa. */
    public static final int HYST_ABS = 15;
    public static final double HYST_PCT = 0.03;
    /** Cổng nhận xe điều hướng mở sớm trước khung cao điểm (phút). */
    public static final int OPEN_EARLY_MINUTES = 30;
    /** Khung quản trị cho phép mặc định của kế hoạch đầu tiên. */
    public static final int MAX_MOVE_TOTAL = 150;
    public static final double MAX_TARGET_PCT = 1.0;
    /** Tỷ lệ sinh viên làm theo; 1,0 khi chưa có số đo (docs/03 mục 4 bước 6). */
    public static final double COMPLIANCE_DEFAULT = 1.0;
}
