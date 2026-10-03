package com.parkflow.backend.controller;

import com.parkflow.backend.ingest.ComplianceService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** `GET /api/plan/{version}/compliance`: tỷ lệ sinh viên làm theo gợi ý của một phiên bản kế hoạch. */
@RestController
@RequestMapping("/api/plan")
@RequiredArgsConstructor
public class ComplianceController {
    private final ComplianceService compliance;

    @GetMapping("/{version}/compliance")
    public Map<String, Object> compliance(@PathVariable long version) {
        return compliance.compliance(version);
    }
}
