package com.parkflow.backend.campusconfig;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Order(0)
@RequiredArgsConstructor
public class CampusConfigBootstrap implements ApplicationRunner {
    private final CampusConfigService service;
    private final ObjectMapper mapper;
    @Override public void run(ApplicationArguments args) throws Exception {
        try (var input = new ClassPathResource("simulator/campus_config.json").getInputStream()) {
            service.seedIfAbsent(mapper.readTree(input));
        }
    }
}
