package com.parkflow.backend.controller;

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

/** Chế độ đăng nhập tắt: `me` trả quản lý trình diễn đúng kiểu giao diện cần; đổi mật khẩu bị từ chối. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired MockMvc mvc;

    @Test void meReturnsTheDemoManagerWithTheFieldsTheUiExpects() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("quanly")).andExpect(jsonPath("$.fullName").isString())
                .andExpect(jsonPath("$.role").value("manager"));
    }

    @Test void loginReturnsTheSameUserAndLogoutIsNoContent() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"a\",\"password\":\"b\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("manager"));
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test void changingAPasswordIsRefusedBecauseThereAreNoAccounts() throws Exception {
        mvc.perform(post("/api/auth/password").contentType(MediaType.APPLICATION_JSON).content("{\"current\":\"a\",\"next\":\"b\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("AUTH_DISABLED"));
    }
}
