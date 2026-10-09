package io.github.yavonalabs.vectis.core.web;

import io.github.yavonalabs.vectis.core.metadata.EntityDescriptor;
import io.github.yavonalabs.vectis.core.view.SavedViewState;
import java.util.*;

final class AppliedFilters {
    private AppliedFilters() {}
    static List<Map<String, String>> chips(EntityDescriptor descriptor, Map<String, List<String>> input) {
        SavedViewState.capture(descriptor, input);
        List<Map<String, String>> chips = new ArrayList<>();
        String search = input.getOrDefault("search", List.of("")).get(0);
        if (!search.isBlank()) {
            var remaining = new LinkedHashMap<>(input); remaining.remove("search"); remaining.remove("page");
            chips.add(Map.of("label", "Search: " + search, "query", SavedViewState.capture(descriptor, remaining)));
        }
        var fields = input.getOrDefault("filterField", List.of());
        var ops = input.getOrDefault("filterOp", List.of());
        var values = input.getOrDefault("filterValue", List.of());
        Map<String, String> labels = Map.of("eq", "is", "ne", "is not", "contains", "contains", "gt", ">", "gte", "≥", "lt", "<", "lte", "≤", "empty", "is not provided", "notEmpty", "is provided");
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).isBlank()) continue;
            String field = fields.get(i);
            String label = descriptor.fields().stream().filter(f -> f.name().equals(field)).findFirst().orElseThrow().displayName();
            var remaining = new LinkedHashMap<>(input); remaining.remove("page");
            for (String key : List.of("filterField", "filterOp", "filterValue")) {
                var copy = new ArrayList<>(input.get(key)); copy.remove(i); remaining.put(key, copy);
            }
            chips.add(Map.of("label", label + " " + labels.get(ops.get(i)) + (Set.of("empty", "notEmpty").contains(ops.get(i)) ? "" : " " + values.get(i)),
                    "query", SavedViewState.capture(descriptor, remaining)));
        }
        return chips;
    }
}
