package io.github.yavonalabs.vectis.core.web;

import io.github.yavonalabs.vectis.core.export.RecordExportService;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${vectis.path:/admin}")
public class RecordExportController {
    private final RecordExportService exports;
    public RecordExportController(RecordExportService exports) { this.exports = exports; }
    @GetMapping("/{slug}/export.csv")
    public ResponseEntity<byte[]> export(@PathVariable String slug, @RequestParam MultiValueMap<String, String> input) {
        byte[] content = exports.export(slug, input);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vectis-records.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff").body(content);
    }
}
