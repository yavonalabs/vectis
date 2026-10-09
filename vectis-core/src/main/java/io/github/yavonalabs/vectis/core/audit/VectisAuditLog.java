package io.github.yavonalabs.vectis.core.audit;

import io.github.yavonalabs.vectis.core.event.VectisChangeEvent;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "vectis_audit_logs", indexes = {
    @Index(name = "idx_vectis_audit_entity", columnList = "entity_slug, encoded_entity_id"),
    @Index(name = "idx_vectis_audit_ts", columnList = "timestamp")
})
public class VectisAuditLog {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "entity_slug", nullable = false, length = 100)
    private String entitySlug;

    @Column(name = "encoded_entity_id", nullable = false, length = 255)
    private String encodedEntityId;

    @Column(name = "action_name", nullable = false, length = 150)
    private String actionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VectisChangeEvent.OperationType operation;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Column(length = 1000)
    private String reason;
    @Column(name = "operation_id", length = 36)
    private String operationId;
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }

    @Lob
    @Column(name = "before_snapshot_json")
    private String beforeSnapshotJson;

    @Lob
    @Column(name = "after_snapshot_json")
    private String afterSnapshotJson;

    @Column(nullable = false)
    private Instant timestamp;

    public VectisAuditLog() {}

    public VectisAuditLog(
            String id,
            String entitySlug,
            String encodedEntityId,
            String actionName,
            VectisChangeEvent.OperationType operation,
            String actorUsername,
            String reason,
            String beforeSnapshotJson,
            String afterSnapshotJson,
            Instant timestamp
    ) {
        this.id = id;
        this.entitySlug = entitySlug;
        this.encodedEntityId = encodedEntityId;
        this.actionName = actionName;
        this.operation = operation;
        this.actorUsername = actorUsername;
        this.reason = reason;
        this.beforeSnapshotJson = beforeSnapshotJson;
        this.afterSnapshotJson = afterSnapshotJson;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEntitySlug() { return entitySlug; }
    public void setEntitySlug(String entitySlug) { this.entitySlug = entitySlug; }
    public String getEncodedEntityId() { return encodedEntityId; }
    public void setEncodedEntityId(String encodedEntityId) { this.encodedEntityId = encodedEntityId; }
    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }
    public VectisChangeEvent.OperationType getOperation() { return operation; }
    public void setOperation(VectisChangeEvent.OperationType operation) { this.operation = operation; }
    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getBeforeSnapshotJson() { return beforeSnapshotJson; }
    public void setBeforeSnapshotJson(String beforeSnapshotJson) { this.beforeSnapshotJson = beforeSnapshotJson; }
    public String getAfterSnapshotJson() { return afterSnapshotJson; }
    public void setAfterSnapshotJson(String afterSnapshotJson) { this.afterSnapshotJson = afterSnapshotJson; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
