package io.github.yavonalabs.vectis.core.mutation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Opaque database-backed proposals. The reason is audit context, never handler business input. */
public class ActionProposalStore {
    private final EntityManager em;
    public ActionProposalStore(EntityManager em) { this.em = em; }

    public static Map<String, String> businessInput(Map<String, String> input) {
        MutationInputs.validate(input);
        var business = new TreeMap<>(input);
        business.keySet().removeAll(Set.of("_csrf", "_list", "_operation", "_proposal", "_version", "_reason"));
        return Collections.unmodifiableMap(business);
    }

    @Transactional
    public String issue(String actor, String slug, String action, String record, String version, Map<String, String> input) {
        String id = UUID.randomUUID().toString();
        em.persist(new ActionProposal(id, fingerprint(actor, slug, action, record, version, input)));
        return id;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(String actor, String slug, String action, String record, String version, Map<String, String> input) {
        String id = input.get("_proposal");
        if (id == null || !id.matches("[0-9a-f-]{36}")) throw rejected();
        ActionProposal proposal = em.find(ActionProposal.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (proposal == null || !proposal.valid(fingerprint(actor, slug, action, record, version, input))) throw rejected();
        proposal.consume();
    }

    private ResponseStatusException rejected() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "This review is missing, expired or no longer valid. Review the action again.");
    }
    private String fingerprint(String actor, String slug, String action, String record, String version, Map<String, String> input) {
        try {
            String json = new ObjectMapper().writeValueAsString(List.of(actor, slug, action, record, version, businessInput(input)));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("Cannot fingerprint review", e); }
    }
}
