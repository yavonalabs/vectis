package io.github.yavonalabs.vectis.core.view;

import io.github.yavonalabs.vectis.core.metadata.EntityDescriptor;
import io.github.yavonalabs.vectis.core.query.RecordFilter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Version 1 stores only list controls. Page is validated but resets on opening. */
public final class SavedViewState {
    private static final Set<String> KEYS = Set.of("search", "sort", "dir", "size", "page", "filterField", "filterOp", "filterValue");
    private SavedViewState() {}
    public static String capture(EntityDescriptor descriptor, Map<String, List<String>> parameters) {
        List<String> parts = new ArrayList<>();
        parameters.forEach((key, group) -> {
            if (KEYS.contains(key)) {
                if (group == null || group.size() > (key.startsWith("filter") ? 3 : 1)) throw invalid();
                group.forEach(value -> {
                    if (value == null || value.length() > 200) throw invalid();
                    parts.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
                });
            }
        });
        return validate(descriptor, String.join("&", parts));
    }
    public static String validate(EntityDescriptor descriptor, String query) {
        if (query == null || query.length() > 8192) throw invalid();
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (!query.isEmpty()) {
            String[] pairs = query.split("&", -1);
            if (pairs.length > 14) throw invalid();
            for (String pair : pairs) {
                String[] parts = pair.split("=", 2);
                if (parts.length != 2) throw invalid();
                String key, value;
                try {
                    key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                    value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ex) { throw invalid(); }
                if (!KEYS.contains(key) || value.length() > 200 || value.chars().anyMatch(Character::isISOControl)) throw invalid();
                var group = values.computeIfAbsent(key, ignored -> new ArrayList<>());
                if (!key.startsWith("filter") && !group.isEmpty()) throw invalid();
                group.add(value);
            }
        }
        String sort = first(values, "sort", "");
        if (!sort.isBlank() && descriptor.fields().stream().noneMatch(f -> f.name().equals(sort))) throw invalid();
        if (!Set.of("asc", "desc").contains(first(values, "dir", "asc"))) throw invalid();
        if (!Set.of("10", "25", "50").contains(first(values, "size", "10"))) throw invalid();
        try { if (Integer.parseInt(first(values, "page", "0")) < 0) throw invalid(); }
        catch (NumberFormatException ex) { throw invalid(); }
        RecordFilter.parse(descriptor, values.getOrDefault("filterField", List.of()),
                values.getOrDefault("filterOp", List.of()), values.getOrDefault("filterValue", List.of()));
        values.remove("page");
        List<String> result = new ArrayList<>();
        values.forEach((key, group) -> group.forEach(value -> result.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8))));
        String canonical = String.join("&", result);
        if (canonical.length() > 8192) throw invalid();
        return canonical;
    }
    private static String first(Map<String, List<String>> values, String key, String fallback) {
        return values.containsKey(key) ? values.get(key).get(0) : fallback;
    }
    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This saved view is invalid or uses unavailable fields. Rebuild its filters from the record list.");
    }
}
