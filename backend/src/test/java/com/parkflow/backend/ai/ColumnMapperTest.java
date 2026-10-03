package com.parkflow.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Ánh xạ cột: dự phòng theo tên, chuẩn hóa kết quả AI, không gửi mã thẻ. Không cần Postgres. */
class ColumnMapperTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ColumnMapper columnMapper = new ColumnMapper(mock(AiService.class), new AiMode(true), mapper);   // Mock: không gọi mô hình

    private JsonNode request(String kind, String... names) {
        var request = mapper.createObjectNode().put("kind", kind);
        var columns = request.putArray("columns");
        for (String name : names) columns.addObject().put("name", name).putArray("samples").add("a").add("b");
        return request;
    }

    private JsonNode find(JsonNode result, String column) {
        for (JsonNode m : result.path("mappings")) if (m.path("column").asString().equals(column)) return m;
        throw new AssertionError("không có cột " + column);
    }

    private ObjectNode json(String text) { return (ObjectNode) mapper.readTree(text); }

    @Test void exactAndVietnameseColumnNamesMapWithHighConfidence() {
        var result = columnMapper.map(request("swipe_log", "Thời gian vào", "Giờ ra", "Cổng", "Mã thẻ", "Bãi", "Loại xe"));
        assertEquals("time_in", find(result, "Thời gian vào").path("field").asString());
        assertEquals("time_out", find(result, "Giờ ra").path("field").asString());
        assertEquals("gate", find(result, "Cổng").path("field").asString());
        assertEquals("card_id", find(result, "Mã thẻ").path("field").asString());
        assertEquals("lot", find(result, "Bãi").path("field").asString());
        assertEquals("vehicle_type", find(result, "Loại xe").path("field").asString());
        assertFalse(result.path("needsReview").asBoolean());
    }

    @Test void anUnrecognisedColumnIsIgnoredAndNeedsReview() {
        var result = columnMapper.map(request("swipe_log", "time_in", "Ghi chú"));
        assertEquals("ignore", find(result, "Ghi chú").path("field").asString());
        assertTrue(find(result, "Ghi chú").path("needsReview").asBoolean());
        assertTrue(result.path("needsReview").asBoolean());
    }

    @Test void aPartialNameMatchIsLowConfidence() {
        var result = columnMapper.map(request("swipe_log", "thoi_gian_vao_bai"));
        assertEquals("time_in", find(result, "thoi_gian_vao_bai").path("field").asString());
        assertTrue(find(result, "thoi_gian_vao_bai").path("confidence").asDouble() < 0.8);
        assertTrue(find(result, "thoi_gian_vao_bai").path("needsReview").asBoolean());
    }

    @Test void theScheduleKindUsesItsOwnFields() {
        var result = columnMapper.map(request("schedule", "Mã lớp", "Tòa nhà", "Ngày", "Giờ bắt đầu", "Giờ kết thúc", "Sĩ số"));
        assertEquals("class_id", find(result, "Mã lớp").path("field").asString());
        assertEquals("building", find(result, "Tòa nhà").path("field").asString());
        assertEquals("start_time", find(result, "Giờ bắt đầu").path("field").asString());
        assertEquals("end_time", find(result, "Giờ kết thúc").path("field").asString());
        assertEquals("students", find(result, "Sĩ số").path("field").asString());
    }

    @Test void normalizeGivesOneRowPerColumnInOrderAndDropsUnknownFields() {
        var raw = json("{\"source\":\"groq\",\"mappings\":[{\"column\":\"b\",\"field\":\"salary\",\"confidence\":0.9,\"note\":\"\"}]}");
        var result = columnMapper.normalize(raw, List.of("a", "b"), ColumnMapper.FIELDS.get("swipe_log"));
        assertEquals("a", result.path("mappings").path(0).path("column").asString());
        assertEquals("ignore", result.path("mappings").path(0).path("field").asString());
        assertEquals("ignore", result.path("mappings").path(1).path("field").asString(), "trường lạ thành ignore");
        assertEquals("groq", result.path("source").asString());
    }

    @Test void twoColumnsForTheSameFieldKeepOnlyTheMoreConfidentOne() {
        var raw = json("{\"mappings\":[{\"column\":\"x\",\"field\":\"gate\",\"confidence\":0.6},{\"column\":\"y\",\"field\":\"gate\",\"confidence\":0.95}]}");
        var result = columnMapper.normalize(raw, List.of("x", "y"), ColumnMapper.FIELDS.get("swipe_log"));
        assertEquals("ignore", result.path("mappings").path(0).path("field").asString());
        assertEquals("gate", result.path("mappings").path(1).path("field").asString());
        assertTrue(result.path("mappings").path(0).path("needsReview").asBoolean());
    }

    @Test void confidenceIsClampedToZeroOne() {
        var raw = json("{\"mappings\":[{\"column\":\"x\",\"field\":\"gate\",\"confidence\":7}]}");
        var result = columnMapper.normalize(raw, List.of("x"), ColumnMapper.FIELDS.get("swipe_log"));
        assertEquals(1.0, result.path("mappings").path(0).path("confidence").asDouble());
    }

    @Test void theModelNeverReceivesSamplesOfACardColumn() {
        var ai = mock(AiService.class);
        var sent = new AtomicReference<String>();
        when(ai.run(anyString(), anyString(), any(), any())).thenAnswer(call -> {
            sent.set(call.getArgument(1));
            return json("{\"source\":\"groq\",\"mappings\":[]}");
        });
        var req = mapper.createObjectNode().put("kind", "swipe_log");
        var column = req.putArray("columns").addObject().put("name", "Mã thẻ");
        column.putArray("samples").add("4111111111111111");
        req.withArray("columns").addObject().put("name", "Cổng").putArray("samples").add("Cổng chính");
        new ColumnMapper(ai, new AiMode(false), mapper).map(req);
        assertFalse(sent.get().contains("4111111111111111"), sent.get());
        assertTrue(sent.get().contains("Cổng chính"), "cột thường vẫn gửi mẫu");
    }

    @Test void badRequestsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> columnMapper.map(json("{\"kind\":\"other\",\"columns\":[{\"name\":\"a\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> columnMapper.map(json("{\"kind\":\"swipe_log\",\"columns\":[]}")));
        assertThrows(IllegalArgumentException.class, () -> columnMapper.map(json("{\"kind\":\"swipe_log\",\"columns\":[{\"name\":\" \"}]}")));
    }
}
