package io.github.yavonalabs.vectis.core.mutation;

import jakarta.persistence.*;
import java.time.Instant;

/** Committed receipts share the record/audit transaction. Never exposed as an admin entity. */
@Entity
@Table(name = "vectis_mutation_receipts")
public class MutationReceipt {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false, length = 64) private String fingerprint;
    @Column(length = 255) private String resultId;
    @Column(nullable = false) private boolean completed;
    @Column(nullable = false) private Instant createdAt;
    @Column(length = 200) private String entitySlug;
    @Column(length = 200) private String operation;
    protected MutationReceipt() {}
    MutationReceipt(String id, String fingerprint, String entitySlug, String operation) {
        this.id = id; this.fingerprint = fingerprint; this.createdAt = Instant.now();
        this.entitySlug = entitySlug; this.operation = operation;
    }
    public String entitySlug() { return entitySlug; }
    public String operation() { return operation; }
    public String fingerprint() { return fingerprint; }
    public String resultId() { return resultId; }
    public boolean completed() { return completed; }
    public void complete(String resultId) { this.resultId = resultId; this.completed = true; }
}
