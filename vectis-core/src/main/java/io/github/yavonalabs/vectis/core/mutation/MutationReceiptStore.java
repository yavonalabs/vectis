package io.github.yavonalabs.vectis.core.mutation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class MutationReceiptStore {
    private final EntityManager em;
    private final ObjectMapper json = new ObjectMapper();
    public MutationReceiptStore(EntityManager em) { this.em = em; }

    @Transactional(propagation = Propagation.MANDATORY)
    public MutationReceipt begin(String actor, String operation, String slug, String recordId, Map<String, String> input) {
        MutationInputs.validate(input);
        String key = input.get("_operation");
        if (key == null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid operation key is required. Reload the form.");
        }
        TreeMap<String, String> canonical = new TreeMap<>(input);
        canonical.keySet().removeAll(Set.of("_csrf", "_operation", "_list"));
        String id;
        String fingerprint;
        try {
            id = hash(json.writeValueAsString(List.of(actor, key.toLowerCase(Locale.ROOT))));
            fingerprint = hash(json.writeValueAsString(List.of(operation, slug, recordId == null ? "" : recordId, canonical)));
        } catch (Exception e) { throw new IllegalStateException("Cannot fingerprint operation", e); }
        MutationReceipt existing = em.find(MutationReceipt.class, id);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This operation key was used with different input. Reload and review a new request.");
            }
            if (!existing.completed()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Operation is not complete. Retry the same request later.");
            return existing;
        }
        MutationReceipt receipt = new MutationReceipt(id, fingerprint, slug, operation);
        try {
            em.persist(receipt);
            em.flush();
        } catch (PersistenceException e) {
            // The transaction is rolled back, never reused after a uniqueness/DB error.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The operation could not be reserved. Retry the same request after checking its status.", e);
        }
        return receipt;
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    @Transactional(readOnly = true)
    public MutationReceipt findForActor(String actor, String key) {
        if (key == null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid operation key.");
        try { return em.find(MutationReceipt.class, hash(json.writeValueAsString(List.of(actor, key.toLowerCase(Locale.ROOT))))); }
        catch (Exception e) { throw new IllegalStateException("Cannot read operation result", e); }
    }
}
