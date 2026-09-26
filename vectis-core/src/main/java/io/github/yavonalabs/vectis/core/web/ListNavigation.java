package io.github.yavonalabs.vectis.core.web;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;

/** Carries only list query parameters, never a user-supplied redirect destination. */
public final class ListNavigation {
    private static final Set<String> KEYS = Set.of("search", "sort", "dir", "page", "size", "filterField", "filterOp", "filterValue");
    private ListNavigation() {}

    public static String sanitize(String query) {
        if (query == null || query.length() > 8192) return "";
        var parts = new ArrayList<String>();
        try {
            for (String pair : query.split("&")) {
                String[] entry = pair.split("=", 2);
                String key = URLDecoder.decode(entry[0], StandardCharsets.UTF_8);
                if (!KEYS.contains(key)) continue;
                String value = entry.length == 2 ? URLDecoder.decode(entry[1], StandardCharsets.UTF_8) : "";
                if (value.length() > 200 || value.chars().anyMatch(c -> Character.isISOControl(c))) return "";
                parts.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
                if (parts.size() > 15) return "";
            }
            return String.join("&", parts);
        } catch (IllegalArgumentException ex) { return ""; }
    }

    public static String querySuffix(String query) {
        String safe = sanitize(query);
        return safe.isEmpty() ? "" : "?" + safe;
    }

    public static String contextSuffix(String query) {
        String safe = sanitize(query);
        return safe.isEmpty() ? "" : "?_list=" + URLEncoder.encode(safe, StandardCharsets.UTF_8);
    }
}
