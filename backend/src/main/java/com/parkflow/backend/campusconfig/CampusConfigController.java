package com.parkflow.backend.campusconfig;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequiredArgsConstructor
public class CampusConfigController {
    private final CampusConfigService service;
    @GetMapping("/api/config")
    public ObjectNode get() { return service.currentWithCounts(); }
}
