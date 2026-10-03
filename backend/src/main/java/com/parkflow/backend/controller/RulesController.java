package com.parkflow.backend.controller;

import com.parkflow.backend.rules.RuleDraft;
import com.parkflow.backend.rules.RuleStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** `GET/POST /api/rules`, `PATCH/DELETE /api/rules/{id}`: quy tắc đã được quản trị xem trước và xác nhận (docs/thiet-ke/openapi.yaml). */
@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RulesController {
    private final RuleStore store;

    @GetMapping
    public List<RuleStore.Rule> list() {
        return store.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleStore.Rule create(@RequestBody RuleDraft draft) {
        return store.create(draft);
    }

    @PatchMapping("/{id}")
    public RuleStore.Rule patch(@PathVariable long id, @RequestBody JsonNode body) {
        return store.patch(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        store.delete(id);
    }
}
