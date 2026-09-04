package io.github.yavonalabs.vectis.core.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yavonalabs.vectis.core.event.VectisChangeEvent;
import jakarta.persistence.EntityManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class VectisAuditLogService {

    private final EntityManager entityManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VectisAuditLogService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onVectisChangeEvent(VectisChangeEvent event) {
        try {
            String beforeJson = event.getBeforeSnapshot() != null ? objectMapper.writeValueAsString(event.getBeforeSnapshot()) : null;
            String afterJson = event.getAfterSnapshot() != null ? objectMapper.writeValueAsString(event.getAfterSnapshot()) : null;

            VectisAuditLog log = new VectisAuditLog(
                    UUID.randomUUID().toString(),
                    event.getEntitySlug(),
                    String.valueOf(event.getEntityId()),
                    event.getActionName(),
                    event.getOperation(),
                    event.getActorUsername(),
                    event.getReason(),
                    beforeJson,
                    afterJson,
                    event.getEventInstant() != null ? event.getEventInstant() : Instant.now()
            );

            entityManager.persist(log);
        } catch (Exception e) {
            System.err.println("[Vectis] Failed to record audit log: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<VectisAuditLog> findRecent(int limit) {
        return entityManager.createQuery(
                "SELECT l FROM VectisAuditLog l ORDER BY l.timestamp DESC", VectisAuditLog.class)
                .setMaxResults(limit)
                .getResultList();
    }

    @Transactional(readOnly = true)
    public List<VectisAuditLog> findByEntity(String entitySlug, String encodedEntityId) {
        return entityManager.createQuery(
                "SELECT l FROM VectisAuditLog l WHERE l.entitySlug = :slug AND l.encodedEntityId = :entityId ORDER BY l.timestamp DESC",
                VectisAuditLog.class)
                .setParameter("slug", entitySlug)
                .setParameter("entityId", encodedEntityId)
                .setMaxResults(50)
                .getResultList();
    }
}