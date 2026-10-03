package com.parkflow.backend.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Lỗi định tuyến trả đúng mã HTTP trong cùng dạng {error, message}: không có địa chỉ là 404, sai phương thức là 405, sai định dạng là 415. */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorRoutingTest {
    @Autowired MockMvc mvc;

    @Test void anUnknownPathIs404NotAnInternalError() throws Exception {
        mvc.perform(get("/api/khong-co")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test void aWrongMethodIs405() throws Exception {
        mvc.perform(delete("/api/lots/status")).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    @Test void aWrongContentTypeIs415() throws Exception {
        mvc.perform(post("/api/ai/chat").contentType(MediaType.TEXT_PLAIN).content("xin chào"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.error").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}
