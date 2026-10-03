package com.parkflow.backend.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.parkflow.backend.ai.AiMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** `/api/ai/*` đầu cuối. Không có khóa nên không gọi dịch vụ thật: chỉ nhánh Mock và nhánh hết nhà cung cấp. */
@SpringBootTest
@AutoConfigureMockMvc
class AiControllerTest {
    @Autowired MockMvc mvc;
    @Autowired AiMode mode;

    @AfterEach void reset() { mode.setMock(false); }

    private org.springframework.test.web.servlet.ResultActions chat(String json) throws Exception {
        return mvc.perform(post("/api/ai/chat").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test void withTheMockSwitchOnTheAnswerIsMarkedAsSampleData() throws Exception {
        mvc.perform(put("/api/ai/mode").contentType(MediaType.APPLICATION_JSON).content("{\"mock\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mock").value(true));
        chat("{\"question\":\"Sáng mai có bãi nào nguy cơ quá tải không?\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("mock"))
                .andExpect(jsonPath("$.answer").isString()).andExpect(jsonPath("$.suggestions.length()").value(3));
        mvc.perform(get("/api/ai/mode")).andExpect(jsonPath("$.mock").value(true));
    }

    @Test void withNoProviderKeyAndMockOffTheErrorCarriesAMockFallbackWith503() throws Exception {
        mode.setMock(false);
        chat("{\"question\":\"Tuần này bãi nào căng nhất?\"}")
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.fallback.source").value("mock")).andExpect(jsonPath("$.fallback.answer").isString());
    }

    @Test void aMissingOrOverlongQuestionIsABadRequest() throws Exception {
        chat("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        chat("{\"question\":\"" + "x".repeat(501) + "\"}").andExpect(status().isBadRequest());
        chat("{\"question\":\"   \"}").andExpect(status().isBadRequest());
    }

    @Test void theModeSwitchRejectsANonBooleanValue() throws Exception {
        mvc.perform(put("/api/ai/mode").contentType(MediaType.APPLICATION_JSON).content("{\"mock\":\"có\"}"))
                .andExpect(status().isBadRequest());
        assertTrue(!mode.mock());
    }
}
