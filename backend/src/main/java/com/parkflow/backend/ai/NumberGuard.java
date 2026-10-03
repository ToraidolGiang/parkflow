package com.parkflow.backend.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Chặn số sai: mọi con số đi kèm dấu % trong câu trả lời phải là một số có trong FACTS (cho phép làm tròn đến một chữ số thập phân
 * hoặc đến số nguyên) hoặc là một ngưỡng thiết kế 80, 100, 120. Mô hình diễn giải chứ không được tự tính ra số mới.
 */
final class NumberGuard {
    private static final Pattern PERCENT = Pattern.compile("([0-9]+(?:[.,][0-9]+)?)\s*%");
    private static final double[] THRESHOLDS = {80, 100, 120};

    private NumberGuard() {}

    static boolean onlyKnownPercentages(String answer, JsonNode facts) {
        if (answer == null) return true;
        List<Double> known = new ArrayList<>();
        collect(facts, known);
        Matcher matcher = PERCENT.matcher(answer);
        while (matcher.find()) {
            double stated = Double.parseDouble(matcher.group(1).replace(',', '.'));
            if (!isKnown(stated, known)) return false;
        }
        return true;
    }

    private static boolean isKnown(double stated, List<Double> known) {
        for (double threshold : THRESHOLDS) if (stated == threshold) return true;
        for (double value : known) {
            if (Math.abs(value - stated) < 0.051 || Math.round(value) == Math.round(stated) && stated == Math.rint(stated)) return true;
        }
        return false;
    }

    private static void collect(JsonNode node, List<Double> out) {
        if (node.isNumber()) out.add(node.asDouble());
        else node.forEach(child -> collect(child, out));
    }
}
