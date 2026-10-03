package com.parkflow.backend.importer;

import com.parkflow.backend.common.ApiKeyGuard;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {
    public record ConfirmRequest(List<ImportService.Mapping> mappings) {}
    public record ConfirmWithId(String importId, List<ImportService.Mapping> mappings) {}
    private final ImportService imports;
    private final ApiKeyGuard guard;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ObjectNode upload(@RequestHeader(value="X-Api-Key",required=false) String key,
            @RequestParam("file") MultipartFile file, @RequestParam(value="kind",defaultValue="swipe_log") String kind) {
        guard.check(key);
        return imports.upload(kind,file);
    }

    @PostMapping("/{importId}/confirm")
    public ObjectNode confirm(@RequestHeader(value="X-Api-Key",required=false) String key,
            @PathVariable String importId, @RequestBody ConfirmRequest body) {
        guard.check(key);
        return imports.confirm(importId,body.mappings());
    }

    @PostMapping("/confirm")
    public ObjectNode confirmWithId(@RequestHeader(value="X-Api-Key",required=false) String key,
            @RequestBody ConfirmWithId body) {
        guard.check(key);
        return imports.confirm(body.importId(),body.mappings());
    }
}
