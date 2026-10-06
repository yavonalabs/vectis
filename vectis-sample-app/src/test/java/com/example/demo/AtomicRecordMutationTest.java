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
    @SpyBean DynamicCriteriaQueryEngine queries;
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
        records.save("employee", Map.of("__id", fixture.getId().toString(), "version", fixture.getVersion().toString(), "firstName", "Updated", "_reason", "Atomic success"));
        assertThat(reload().getFirstName()).isEqualTo("Updated");
        var logs = audit.findByEntity("employee", fixture.getId().toString());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getActorUsername()).isEqualTo("atomic-reviewer");
        assertThat(logs.get(0).getAfterSnapshotJson()).contains("Updated").doesNotContain("SECRET_HASH");
    }

    @Test void updateRollsBackEvenAfterAuditWasFlushed() {
        failAfterAuditFlush();
        assertThatThrownBy(() -> records.save("employee", Map.of("__id", fixture.getId().toString(), "version", fixture.getVersion().toString(), "firstName", "RolledBack", "_reason", "Rollback")))
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
            assertThatThrownBy(() -> records.save("employee", Map.of("__id", fixture.getId().toString(), "version", fixture.getVersion().toString(),
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
        records.delete("employee", id, Map.of("_version", queries.<Employee>findById(metadata.getBySlug("employee").orElseThrow(), Long.valueOf(id)).getVersion().toString(), "_reason", "Delete success"));
        assertThat(queries.<Employee>findById(metadata.getBySlug("employee").orElseThrow(), Long.valueOf(id))).isNull();
        assertThat(audit.findByEntity("employee", id)).hasSize(2);
    }

    @Test void deleteRollsBackEvenAfterAuditWasFlushed() {
        failAfterAuditFlush();
        assertThatThrownBy(() -> records.delete("employee", fixture.getId().toString(), Map.of("_version", fixture.getVersion().toString(), "_reason", "Rollback")))
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
        assertThatThrownBy(() -> records.save("employee", Map.of("__id", fixture.getId().toString(), "version", fixture.getVersion().toString(), "email", duplicate.getEmail(), "_reason", "Constraint")))
                .isInstanceOf(RuntimeException.class);
        assertThat(reload().getEmail()).isEqualTo(fixture.getEmail());
        assertThat(auditCount()).isZero();
    }

    @Test void missingMalformedAndStaleVersionsRejectWithoutWrites() throws Exception {
        for (String version : new String[]{"", "invalid", "-1"}) {
            assertThatThrownBy(() -> records.save("employee", Map.of("__id", fixture.getId().toString(),
                    "version", version, "firstName", "Rejected", "_reason", "Version check")))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThatThrownBy(() -> records.delete("employee", fixture.getId().toString(),
                    Map.of("_version", version, "_reason", "Version check")))
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
        assertThatThrownBy(() -> records.save("employee", Map.of("__id", fixture.getId().toString(), "_reason", "Missing")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> records.delete("employee", fixture.getId().toString(), Map.of("_reason", "Missing")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(reload().getFirstName()).isEqualTo("Atomic");
        assertThat(auditCount()).isZero();
    }

    @Test void deleteCannotUseVersionFromBeforeAnEdit() throws Exception {
        records.save("employee", Map.of("__id", fixture.getId().toString(), "version", fixture.getVersion().toString(),
                "firstName", "Updated", "_reason", "Update before delete"));
        assertThatThrownBy(() -> records.delete("employee", fixture.getId().toString(),
                Map.of("_version", fixture.getVersion().toString(), "_reason", "Stale delete")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
        assertThat(reload()).isNotNull();
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test void concurrentEditsOfSameVersionProduceOneCommitAndOneConflict() throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var target = org.springframework.test.util.AopTestUtils.<DynamicCriteriaQueryEngine>getUltimateTargetObject(queries);
        doAnswer(call -> {
            Object loaded = call.callRealMethod();
            if (reads.incrementAndGet() <= 2) {
                ready.countDown();
                if (!ready.await(30, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent read timed out");
            }
            return loaded;
        }).when(target).findById(any(), org.mockito.ArgumentMatchers.eq(fixture.getId()));
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.List<java.util.concurrent.Future<Object>> jobs = new java.util.ArrayList<>();
            for (String name : java.util.List.of("WriterOne", "WriterTwo")) {
                jobs.add(pool.submit(() -> {
                    var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(auth);
                    org.springframework.security.core.context.SecurityContextHolder.setContext(context);
                    try {
                        return records.save("employee", Map.of("__id", fixture.getId().toString(),
                                "version", fixture.getVersion().toString(), "firstName", name, "_reason", "Concurrent edit"));
                    } catch (RuntimeException ex) { return ex; }
                    finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
                }));
            }
            var results = java.util.List.of(jobs.get(0).get(45, java.util.concurrent.TimeUnit.SECONDS),
                    jobs.get(1).get(45, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(results.stream().filter(String.class::isInstance).count()).isEqualTo(1);
            var failure = results.stream().filter(Throwable.class::isInstance).findFirst().orElseThrow();
            assertThat(failure).isInstanceOfAny(jakarta.persistence.OptimisticLockException.class,
                    org.springframework.dao.OptimisticLockingFailureException.class);
            assertThat(reload().getFirstName()).isIn("WriterOne", "WriterTwo");
            assertThat(auditCount()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
}
