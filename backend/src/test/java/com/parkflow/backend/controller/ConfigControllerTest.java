package com.parkflow.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** `GET /api/config` trả cấu hình trường đóng gói cùng ứng dụng: bãi, cổng, tòa nhà, tuyến, ngưỡng. */
@SpringBootTest
@AutoConfigureMockMvc
class ConfigControllerTest {
    @Autowired MockMvc mvc;

    @Test void returnsTheCampusConfigurationWithItsMainSections() throws Exception {
        mvc.perform(get("/api/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots").isArray()).andExpect(jsonPath("$.lots.length()").value(4))
                .andExpect(jsonPath("$.gates").isArray()).andExpect(jsonPath("$.buildings").isArray())
                .andExpect(jsonPath("$.routes").isArray()).andExpect(jsonPath("$.thresholds").exists());
    }

    @Test void everyLotHasAnIdAndACapacity() throws Exception {
        mvc.perform(get("/api/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lots[*].id").exists()).andExpect(jsonPath("$.lots[*].capacity").exists());
    }
}
