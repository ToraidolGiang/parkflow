package com.parkflow.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MockAiTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MockAi mock = new MockAi(mapper);

    @Test void buildsTheAnswerFromForecastPeaksAndFlagsOnlyLotsOverCapacity() {
        var facts = mapper.readTree("""
                {"forecastPeaks":[{"lotId":"A","time":"07:45","pct":113.04},{"lotId":"B","time":"07:30","pct":88.9}],"plan":{"version":7,"status":"pending_approval"}}
                """);
        var result = mock.chat(facts);
        String answer = result.path("answer").asText();
        assertTrue(answer.startsWith("**Dữ liệu mẫu.**"));
        assertTrue(answer.contains("Bãi A dự báo đạt 113,0% lúc 07:45"), answer);
        assertTrue(!answer.contains("Bãi B dự báo đạt"), "bãi dưới 100% không bị báo quá tải");
        assertTrue(answer.contains("phiên bản 7"));
        assertTrue(answer.endsWith("Phương án cần quản trị duyệt trước khi áp dụng."));
        assertEquals("113,0%", result.path("facts").path(0).path("value").asText());
        assertEquals(2, result.path("facts").size());
        assertEquals(3, result.path("suggestions").size());
    }

    @Test void saysNoRiskWhenNoPeakReachesTheCapacity() {
        var result = mock.chat(mapper.readTree("{\"forecastPeaks\":[{\"lotId\":\"A\",\"time\":\"07:45\",\"pct\":70.0}]}"));
        assertTrue(result.path("answer").asText().contains("Chưa bãi nào dự báo vượt sức chứa"));
    }

    @Test void withoutAForecastItNeverClaimsThereIsNoRiskFromCurrentCounts() {
        var result = mock.chat(mapper.readTree("{\"lots\":[{\"lotId\":\"A\",\"pct\":30.0}],\"forecastPeaks\":[]}"));
        String answer = result.path("answer").asText();
        assertTrue(answer.contains("Bãi A đang 30,0%"), answer);
        assertTrue(answer.contains("Chưa có dự báo"), answer);
        assertTrue(!answer.contains("Chưa bãi nào dự báo vượt"), "không suy ra 'không quá tải' từ số xe hiện tại");
    }

    @Test void withNoDataAtAllItSaysSo() {
        var result = mock.chat(mapper.readTree("{}"));
        assertTrue(result.path("answer").asText().contains("Chưa có số liệu về các bãi"));
        assertEquals(0, result.path("facts").size());
    }
}
