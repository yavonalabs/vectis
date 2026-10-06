package com.example.demo;

import com.example.demo.entity.Employee;
import io.github.yavonalabs.vectis.core.audit.VectisAuditLogService;
import io.github.yavonalabs.vectis.core.metadata.EntityMetadataRegistry;
import io.github.yavonalabs.vectis.core.mutation.RecordMutationService;
import io.github.yavonalabs.vectis.core.query.DynamicCriteriaQueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.test.context.support.WithMockUser;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** No test-owned transaction: assertions read after the mutation's commit or rollback. */
@SpringBootTest(properties = {
        "spring.datasource.url=${VECTIS_TEST_DB_URL:jdbc:h2:mem:atomicrecords;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=${VECTIS_TEST_DB_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${VECTIS_TEST_DB_USER:sa}",
        "spring.datasource.password=${VECTIS_TEST_DB_PASSWORD:}"})
@WithMockUser(username = "atomic-reviewer", roles = "ADMIN")
class AtomicRecordMutationTest {
    @Autowired RecordMutationService records;
    @Autowired DynamicCriteriaQueryEngine queries;
    @Autowired EntityMetadataRegistry metadata;
    @SpyBean VectisAuditLogService audit;
    Employee fixture;

    @BeforeEach void createFixture() {
        fixture = new Employee("Atomic", "Fixture", UUID.randomUUID() + "@example.com", new BigDecimal("60000"), null);
        queries.persist(fixture);
    }

    Employee reload() { return queries.findById(metadata.getBySlug("employee").orElseThrow(), fixture.getId()); }
    long auditCount() { return audit.findByEntity("employee", fixture.getId().toString()).size(); }

    void failAfterAuditFlush() {
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Injected failure after audit flush"); })
                .when(org.springframework.test.util.AopTestUtils.<VectisAuditLogService>getUltimateTargetObject(audit)).recordMutation(any());
    }

    @Test void updateAndAuditCommitTogetherOnce() throws Exception {
        records.save("employee", Map.of("__id", fixture.getId().toString(), "firstName", "Updated", "_reason", "Atomic success"));
        assertThat(reload().getFirstName()).isEqualTo("Updated");
        var logs = audit.findByEntity("employee", fixture.getId().toString());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getActorUsername()).isEqualTo("atomic-reviewer");
        assertThat(logs.get(0).getAfterSnapshotJson()).contains("Updated").doesNotContain("SECRET_HASH");
    }

    @Test void updateRollsBackEvenAfterAuditWasFlushed() {
        failAfterAuditFlush();
        assertThatThrownBy(() -> records.save("employee", Map.of("__id", fixture.getId().toString(), "firstName", "RolledBack", "_reason", "Rollback")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reload().getFirstName()).isEqualTo("Atomic");
        assertThat(auditCount()).isZero();
    }

    @Test void auditInsertConstraintFailureRollsBackRecordUpdate() {
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        var previous = context.getAuthentication();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "x".repeat(101), "unused", previous.getAuthorities()));
        try {
            assertThatThrownBy(() -> records.save("employee", Map.of("__id", fixture.getId().toString(),
                    "firstName", "Uncommitted", "_reason", "Audit database failure"))).isInstanceOf(RuntimeException.class);
        } finally {
            context.setAuthentication(previous);
        }
        assertThat(reload().getFirstName()).isEqualTo("Atomic");
        assertThat(auditCount()).isZero();
    }

    @Test void createAndDeleteEachCommitOneAudit() throws Exception {
        String id = records.save("employee", Map.of("firstName", "New", "lastName", "Fixture",
                "email", UUID.randomUUID() + "@example.com", "salary", "60000", "_reason", "Create success"));
        assertThat(audit.findByEntity("employee", id)).hasSize(1);
        records.delete("employee", id, Map.of("_reason", "Delete success"));
        assertThat(queries.<Employee>findById(metadata.getBySlug("employee").orElseThrow(), Long.valueOf(id))).isNull();
        assertThat(audit.findByEntity("employee", id)).hasSize(2);
    }

    @Test void deleteRollsBackEvenAfterAuditWasFlushed() {
        failAfterAuditFlush();
        assertThatThrownBy(() -> records.delete("employee", fixture.getId().toString(), Map.of("_reason", "Rollback")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reload()).isNotNull();
        assertThat(auditCount()).isZero();
    }

    @Test void createRollsBackEvenAfterAuditWasFlushed() {
        String email = UUID.randomUUID() + "@example.com";
        failAfterAuditFlush();
        assertThatThrownBy(() -> records.save("employee", Map.of("firstName", "New", "lastName", "Fixture",
                "email", email, "salary", "60000", "_reason", "Rollback"))).isInstanceOf(IllegalStateException.class);
        assertThat(queries.findPage(metadata.getBySlug("employee").orElseThrow(), 0, 10, email, null, null).content()).isEmpty();
        assertThat(audit.findRecent(100)).noneMatch(log -> log.getAfterSnapshotJson() != null && log.getAfterSnapshotJson().contains(email));
    }

    @Test void databaseConstraintFailureLeavesNoSuccessAudit() {
        Employee duplicate = new Employee("Other", "Fixture", UUID.randomUUID() + "@example.com", new BigDecimal("60000"), null);
        queries.persist(duplicate);
        assertThatThrownBy(() -> records.save("employee", Map.of("__id", fixture.getId().toString(), "email", duplicate.getEmail(), "_reason", "Constraint")))
                .isInstanceOf(RuntimeException.class);
        assertThat(reload().getEmail()).isEqualTo(fixture.getEmail());
        assertThat(auditCount()).isZero();
    }
}
