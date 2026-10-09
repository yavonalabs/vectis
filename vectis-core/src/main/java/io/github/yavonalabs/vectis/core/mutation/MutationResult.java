package io.github.yavonalabs.vectis.core.mutation;

public record MutationResult(Outcome outcome, String operationId, String recordId, boolean committed,
                             Boolean replayed, String message) {
    public enum Outcome { SUCCEEDED, INVALID, REJECTED, CONFLICT, FAILED, UNKNOWN }
}
