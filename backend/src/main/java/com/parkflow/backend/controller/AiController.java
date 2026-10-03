package com.parkflow.backend.controller;

import com.parkflow.backend.ai.AiMode;
import com.parkflow.backend.ai.AiService;
import com.parkflow.backend.ai.AiUnavailable;
import com.parkflow.backend.ai.ColumnMapper;
import com.parkflow.backend.ai.RuleParser;
import com.parkflow.backend.ai.WeeklyReport;
import com.parkflow.backend.common.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** `POST /api/ai/chat` (trợ lý hỏi đáp) và công tắc dữ liệu mẫu `GET/PUT /api/ai/mode` (docs/thiet-ke/04-api.md, 08-ai-prompts.md). */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiService ai;
    private final AiMode mode;
    private final ColumnMapper columns;
    private final RuleParser ruleParser;
    private final WeeklyReport weeklyReport;

    @PostMapping("/chat")
    public JsonNode chat(@RequestBody JsonNode body) {
        String question = body.path("question").isString() ? body.path("question").asString() : null;
        if (question == null || question.isBlank() || question.length() > AiService.MAX_QUESTION)
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Cần câu hỏi `question` từ 1 đến " + AiService.MAX_QUESTION + " ký tự.");
        List<AiService.Turn> history = new ArrayList<>();
        for (JsonNode turn : body.path("history"))
            history.add(new AiService.Turn(turn.path("role").asString(""), turn.path("text").asString("")));
        return ai.chat(question, history);
    }

    /** Ánh xạ cột tệp người dùng vào trường hệ thống: AI chỉ nhận tên cột và vài giá trị mẫu (không có mã thẻ). */
    @PostMapping("/map-columns")
    public JsonNode mapColumns(@RequestBody JsonNode body) {
        try {
            return columns.map(body);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                    "Cần `kind` (swipe_log hoặc schedule) và `columns` gồm 1 đến 40 cột, mỗi cột có `name`.");
        }
    }

    /** Câu tiếng Việt thành bản nháp quy tắc để xem trước; không lưu gì (lưu bằng POST /api/rules sau khi quản trị xác nhận). */
    @PostMapping("/parse-rule")
    public JsonNode parseRule(@RequestBody JsonNode body) {
        String text = body.path("text").isString() ? body.path("text").asString() : null;
        try {
            return ruleParser.parse(text);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Cần câu `text` từ 1 đến 300 ký tự.");
        }
    }

    /** Báo cáo tuần markdown: `{weekStart: "2026-09-14"}` (ngày đầu tuần); số liệu tổng hợp ở backend, AI chỉ viết lời. */
    @PostMapping("/report")
    public JsonNode report(@RequestBody JsonNode body) {
        java.time.LocalDate weekStart;
        try {
            weekStart = java.time.LocalDate.parse(body.path("weekStart").asString(""));
        } catch (java.time.format.DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Cần `weekStart` dạng yyyy-MM-dd.");
        }
        return weeklyReport.report(weekStart);
    }

    @GetMapping("/mode")
    public Map<String, Object> mode() {
        return Map.of("mock", mode.mock());
    }

    @PutMapping("/mode")
    public Map<String, Object> setMode(@RequestBody JsonNode body) {
        if (!body.path("mock").isBoolean()) throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Cần trường `mock` kiểu true hoặc false.");
        mode.setMock(body.path("mock").asBoolean());
        return Map.of("mock", mode.mock());
    }

    /** Hết nhà cung cấp: 503 kèm câu trả lời dữ liệu mẫu trong `fallback`, để giao diện vẫn hiển thị được. */
    @ExceptionHandler(AiUnavailable.class)
    ResponseEntity<Map<String, Object>> unavailable(AiUnavailable e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", "AI_UNAVAILABLE");
        error.put("message", e.getMessage());
        error.put("fallback", e.fallback());
        return ResponseEntity.status(503).body(error);
    }
}
