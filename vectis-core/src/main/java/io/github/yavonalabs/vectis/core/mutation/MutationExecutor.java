package io.github.yavonalabs.vectis.core.mutation;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import static io.github.yavonalabs.vectis.core.mutation.MutationResult.Outcome.*;

/** Typed adapter over the same authorized service boundaries used by HTML controllers. */
public class MutationExecutor {
    private final RecordMutationService records;
    private final ActionMutationService actions;
    public MutationExecutor(RecordMutationService records, ActionMutationService actions) { this.records = records; this.actions = actions; }

    public MutationResult execute(MutationRequest request) {
        // A synchronous API cannot truthfully claim commit of work inside a caller's transaction.
        if (TransactionSynchronizationManager.isActualTransactionActive())
            return result(request, REJECTED, false, false, "Call this commit-reporting adapter outside a host transaction.");
        try {
            if (request.kind() == MutationRequest.Kind.SAVE
                    && !java.util.Objects.equals(normalize(request.recordId()), normalize(request.input().get("__id"))))
                return result(request, INVALID, false, false, "Record identity must match the submitted save identity.");
            return switch (request.kind()) {
                case SAVE -> new MutationResult(SUCCEEDED, request.operationId(), records.save(request.entity(), request.input()), true, null, "Record committed; an exact retry returns its original result.");
                case DELETE -> {
                    records.delete(request.entity(), request.recordId(), request.input());
                    yield result(request, SUCCEEDED, true, null, "Deletion committed; an exact retry returns its original result.");
                }
                case ACTION -> {
                    var action = actions.execute(request.entity(), request.actionId(), request.recordId(), request.input());
                    yield result(request, SUCCEEDED, action.outcome() == ActionMutationService.Outcome.COMMITTED,
                            action.replayed(), action.outcome() == ActionMutationService.Outcome.COMMITTED ? "Local action committed." : "Host handler completed; downstream delivery is not established.");
                }
            };
        } catch (ResponseStatusException ex) {
            var outcome = switch (ex.getStatusCode().value()) {
                case 400, 422 -> INVALID;
                case 401, 403, 404 -> REJECTED;
                case 409 -> CONFLICT;
                default -> UNKNOWN;
            };
            return result(request, outcome, false, false, ex.getReason() == null ? "Request could not be completed." : ex.getReason());
        } catch (jakarta.validation.ConstraintViolationException ex) {
            return result(request, INVALID, false, false, "Review the permitted fields and business rules.");
        } catch (jakarta.persistence.OptimisticLockException | org.springframework.dao.OptimisticLockingFailureException ex) {
            return result(request, CONFLICT, false, false, "The record changed. Review its current state.");
        } catch (Exception ex) {
            // An exception alone cannot establish whether a commit acknowledgement or external effect was lost.
            return result(request, UNKNOWN, false, false, "Outcome unconfirmed. Inspect the operation and connected systems before retrying.");
        }
    }
    private static String normalize(String value) { return value == null || value.isBlank() ? null : value; }
    private MutationResult result(MutationRequest request, MutationResult.Outcome outcome, boolean committed, Boolean replayed, String message) {
        return new MutationResult(outcome, request.operationId(), request.recordId(), committed, replayed, message);
    }
}
