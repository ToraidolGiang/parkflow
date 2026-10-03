package com.parkflow.backend.controller;

import com.parkflow.backend.approval.PlanVersionService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/plan")
public class PlanController {
    private final PlanVersionService plans;
    public PlanController(PlanVersionService plans) { this.plans=plans; }
    public record Approval(String approvedBy,String option) {}

    @GetMapping("/current")
    public ObjectNode current() { return plans.current(); }

    @PostMapping("/{version}/approve")
    public Map<String,Object> approve(@PathVariable long version,@RequestBody Approval request) {
        var result=plans.approve(version,request.approvedBy(),request.option());
        return Map.of("version",result.version(),"status",result.status().name());
    }
}