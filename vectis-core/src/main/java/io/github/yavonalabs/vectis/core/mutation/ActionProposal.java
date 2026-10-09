package io.github.yavonalabs.vectis.core.mutation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "vectis_action_proposals")
public class ActionProposal {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 64) private String fingerprint;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private boolean consumed;
    protected ActionProposal() {}
    ActionProposal(String id, String fingerprint) {
        this.id = id; this.fingerprint = fingerprint; this.expiresAt = Instant.now().plusSeconds(600);
    }
    boolean valid(String expected) { return !consumed && expiresAt.isAfter(Instant.now()) && fingerprint.equals(expected); }
    void consume() { consumed = true; }
}
