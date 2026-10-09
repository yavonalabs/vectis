package io.github.yavonalabs.vectis.core.mutation;

import java.util.Map;
import java.util.Objects;

/** Server-side integration API. The authenticated actor is deliberately not caller-supplied. */
public record MutationRequest(Kind kind, String entity, String recordId, String actionId, Map<String, String> input) {
    public enum Kind { SAVE, DELETE, ACTION }
    public MutationRequest {
        Objects.requireNonNull(kind); Objects.requireNonNull(entity);
        MutationInputs.validate(input);
        input = Map.copyOf(input);
    }
    public String operationId() { return input.get("_operation"); }
}
