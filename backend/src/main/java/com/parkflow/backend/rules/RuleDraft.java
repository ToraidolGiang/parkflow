package com.parkflow.backend.rules;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Một quy tắc chưa lưu: điều kiện `metric op value` trong khung `from`–`to` cho phạm vi `scope` (ALL hoặc mã bãi), kèm hành động.
 * `routing` chỉ dùng để điều hướng: bắt buộc `forecast_pct`, `>=` hoặc `>`, ngưỡng 50 đến 100 (kế hoạch phân luồng dùng nó làm ngưỡng kích hoạt).
 */
public record RuleDraft(String name, String type, String scope, String from, String to, String metric, String op, Double value, String action) {
    private static final Set<String> TYPES = Set.of("alert", "routing");
    private static final Set<String> METRICS = Set.of("forecast_pct", "current_pct", "inflow_15m");
    private static final Set<String> OPS = Set.of(">=", ">", "<=", "<");
    private static final Pattern HHMM = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");

    /** Danh sách lỗi (rỗng nếu hợp lệ), mỗi lỗi một câu tiếng Việt nói rõ trường nào sai. */
    public List<String> problems(Set<String> lotIds) {
        List<String> problems = new ArrayList<>();
        if (name == null || name.isBlank() || name.length() > 120) problems.add("`name` cần từ 1 đến 120 ký tự.");
        if (type == null || !TYPES.contains(type)) problems.add("`type` phải là alert hoặc routing.");
        if (scope == null || !(scope.equals("ALL") || lotIds.contains(scope))) problems.add("`scope` phải là ALL hoặc mã một bãi có thật.");
        boolean timesOk = from != null && to != null && HHMM.matcher(from).matches() && HHMM.matcher(to).matches();
        if (!timesOk) problems.add("`from` và `to` phải có dạng HH:mm.");
        else if (!parse(from).isBefore(parse(to))) problems.add("`from` phải sớm hơn `to`.");
        if (metric == null || !METRICS.contains(metric)) problems.add("`metric` phải là forecast_pct, current_pct hoặc inflow_15m.");
        if (op == null || !OPS.contains(op)) problems.add("`op` phải là >=, >, <= hoặc <.");
        if (value == null || value.isNaN() || value.isInfinite() || value < 0 || value > 100_000) problems.add("`value` phải là số không âm.");
        if (action == null || action.length() > 300) problems.add("`action` tối đa 300 ký tự.");
        if ("routing".equals(type) && problems.isEmpty()) {
            if (!"forecast_pct".equals(metric) || !(">=".equals(op) || ">".equals(op)))
                problems.add("Quy tắc điều hướng phải dùng forecast_pct với >= hoặc >.");
            else if (value < 50 || value > 100) problems.add("Ngưỡng điều hướng phải từ 50 đến 100.");
        }
        return problems;
    }

    private static LocalTime parse(String hhmm) {
        try {
            return LocalTime.parse(hhmm);
        } catch (DateTimeParseException e) {
            return LocalTime.MIN;
        }
    }
}
