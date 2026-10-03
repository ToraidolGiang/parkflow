package com.parkflow.backend.student;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequiredArgsConstructor
public class StudentController {
    private final StudentService student;

    @GetMapping("/api/student/recommendation")
    public ObjectNode recommend(@RequestParam String building, @RequestParam(required = false) String arrive,
            @RequestParam String sessionId) {
        return student.recommend(building, arrive, sessionId);
    }
}
