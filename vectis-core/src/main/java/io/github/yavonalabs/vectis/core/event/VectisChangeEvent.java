package io.github.yavonalabs.vectis.core.event;

import org.springframework.context.ApplicationEvent;
import java.time.Instant;
import java.util.Map;

public class VectisChangeEvent extends ApplicationEvent {

    public enum OperationType { CREATE, UPDATE, DELETE, ACTION }

    private final String entitySlug;
    private final Object entityId;
    private final String actionName;
    private final OperationType operation;
    private final Map<String, Object> beforeSnapshot;
    private final Map<String, Object> afterSnapshot;
    private final String actorUsername;
    private final String reason;
    private final Instant eventInstant;

    public VectisChangeEvent(
            Object source,
            String entitySlug,
            Object entityId,
            String actionName,
            OperationType operation,
            Map<String, Object> beforeSnapshot,
            Map<String, Object> afterSnapshot,
            String actorUsername,
            String reason
    ) {
        super(source);
        this.entitySlug = entitySlug;
        this.entityId = entityId;
        this.actionName = actionName;
        this.operation = operation;
        this.beforeSnapshot = beforeSnapshot;
        this.afterSnapshot = afterSnapshot;
        this.actorUsername = actorUsername;
        this.reason = reason;
        this.eventInstant = Instant.now();
    }

    public String getEntitySlug() { return entitySlug; }
    public Object getEntityId() { return entityId; }
    public String getActionName() { return actionName; }
    public OperationType getOperation() { return operation; }
    public Map<String, Object> getBeforeSnapshot() { return beforeSnapshot; }
    public Map<String, Object> getAfterSnapshot() { return afterSnapshot; }
    public String getActorUsername() { return actorUsername; }
    public String getReason() { return reason; }
    public Instant getEventInstant() { return eventInstant; }
}