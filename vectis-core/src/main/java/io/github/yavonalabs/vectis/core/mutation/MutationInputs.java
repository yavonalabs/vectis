package io.github.yavonalabs.vectis.core.mutation;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class MutationInputs {
    private MutationInputs() {}
    public static void validate(Map<String, String> input) {
        if (input == null || input.size() > 100 || input.entrySet().stream().anyMatch(e ->
                e.getKey() == null || e.getKey().length() > 200 || e.getValue() == null || e.getValue().length() > 65536)
                || input.values().stream().mapToLong(String::length).sum() > 131072) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mutation input exceeds supported limits.");
        }
    }
}
