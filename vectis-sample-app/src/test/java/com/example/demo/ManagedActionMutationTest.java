package com.example.demo;

import com.example.demo.entity.Employee;
import io.github.yavonalabs.vectis.core.action.*;
import io.github.yavonalabs.vectis.core.audit.VectisAuditLogService;
import io.github.yavonalabs.vectis.core.metadata.EntityMetadataRegistry;
import io.github.yavonalabs.vectis.core.mutation.ActionMutationService;
import io.github.yavonalabs.vectis.core.query.DynamicCriteriaQueryEngine;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.datasource.url=${VECTIS_TEST_DB_URL:jdbc:h2:mem:managedactions;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=${VECTIS_TEST_DB_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${VECTIS_TEST_DB_USER:sa}",
        "spring.datasource.password=${VECTIS_TEST_DB_PASSWORD:}"})
@WithMockUser(username = "action-reviewer", roles = "ADMIN")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class ManagedActionMutationTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired ActionMutationService actions;
    @Autowired io.github.yavonalabs.vectis.core.mutation.MutationExecutor executor;
    @Autowired DynamicCriteriaQueryEngine queries;
    @Autowired EntityMetadataRegistry metadata;
    @Autowired PlatformTransactionManager manager;
    @Autowired io.github.yavonalabs.vectis.core.mutation.ActionProposalStore proposals;
    @SpyBean VectisAuditLogService audit;
    Employee fixture;
    static volatile java.util.concurrent.CyclicBarrier race;

    @TestConfiguration static class Config {
        @Bean EntityActionContributor<Employee> managedActions() {
            return new EntityActionContributor<>() {
                public Class<Employee> getEntityClass() { return Employee.class; }
                public List<EntityAction<Employee>> getActions() {
                    return List.of(EntityAction.<Employee>make("hostFailure").requiresConfirmation(false)
                            .handler((e, p) -> { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Host failed after an unknown external effect"); }),
                            EntityAction.<Employee>make("managedRaise").label("Managed raise")
                            .executionMode(ActionExecutionMode.MANAGED_LOCAL)
                            .requiresConfirmation(false)
                            .handler((e, p) -> {
                                if (p.containsKey("race")) try { race.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                                catch (Exception ex) { throw new IllegalStateException(ex); }
                                e.setSalary(e.getSalary().add(new BigDecimal("100")));
                            }),
                            EntityAction.<Employee>make("managedNoop").executionMode(ActionExecutionMode.MANAGED_LOCAL)
                                    .requiresConfirmation(false)
                                    .handler((e, p) -> {}),
                            EntityAction.<Employee>make("reviewedRaise").executionMode(ActionExecutionMode.MANAGED_LOCAL)
                                    .handler((e, p) -> e.setSalary(e.getSalary().add(new BigDecimal(p.get("amount"))))));
                }
            };
        }
    }

    @BeforeEach void setup() {
        fixture = new Employee("Managed", "Action", UUID.randomUUID() + "@example.com", new BigDecimal("60000"), null);
        queries.persist(fixture);
    }
    Employee reload() { return queries.findById(metadata.getBySlug("employee").orElseThrow(), fixture.getId()); }
    Map<String, String> input() { return Map.of("_operation", UUID.randomUUID().toString(), "_version", fixture.getVersion().toString(), "_reason", "Verified request"); }
    ActionMutationService.Result execute(String action, Map<String, String> input) { return actions.execute("employee", action, fixture.getId().toString(), input); }

    @Test void commitAndReplayHaveExactlyOneLocalEffectAndAudit() {
        var input = input();
        assertThat(execute("managedRaise", input).outcome()).isEqualTo(ActionMutationService.Outcome.COMMITTED);
        assertThat(execute("managedRaise", input).replayed()).isTrue();
        assertThat(reload().getSalary()).isEqualByComparingTo("60100");
        assertThat(audit.findByEntity("employee", fixture.getId().toString())).hasSize(1);
        var changed = new HashMap<>(input); changed.put("_reason", "Different request");
        assertThatThrownBy(() -> execute("managedRaise", changed)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(reload().getSalary()).isEqualByComparingTo("60100");
    }

    @Test void typedAdapterReportsCommitReplayConflictAndUnknownHostOutcome() {
        var request = new io.github.yavonalabs.vectis.core.mutation.MutationRequest(
                io.github.yavonalabs.vectis.core.mutation.MutationRequest.Kind.ACTION,
                "employee", fixture.getId().toString(), "managedRaise", input());
        assertThat(executor.execute(request).committed()).isTrue();
        assertThat(executor.execute(request).replayed()).isTrue();
        var stale = new io.github.yavonalabs.vectis.core.mutation.MutationRequest(request.kind(), request.entity(), request.recordId(), request.actionId(), input());
        assertThat(executor.execute(stale).outcome()).isEqualTo(io.github.yavonalabs.vectis.core.mutation.MutationResult.Outcome.CONFLICT);
        var hostInput = new HashMap<>(input()); hostInput.put("_version", reload().getVersion().toString());
        var host = new io.github.yavonalabs.vectis.core.mutation.MutationRequest(request.kind(), request.entity(), request.recordId(), "hostFailure", hostInput);
        assertThat(executor.execute(host).outcome()).isEqualTo(io.github.yavonalabs.vectis.core.mutation.MutationResult.Outcome.UNKNOWN);
        assertThat(new TransactionTemplate(manager).execute(s -> executor.execute(request)).outcome())
                .isEqualTo(io.github.yavonalabs.vectis.core.mutation.MutationResult.Outcome.REJECTED);
    }

    @Test void typedSaveRejectsConflictingIdentityWithoutChangingData() {
        var input = new HashMap<>(input()); input.put("__id", fixture.getId().toString());
        var request = new io.github.yavonalabs.vectis.core.mutation.MutationRequest(
                io.github.yavonalabs.vectis.core.mutation.MutationRequest.Kind.SAVE, "employee", "different-record", null, input);
        input.put("__id", "changed-after-construction");
        assertThat(request.input().get("__id")).isEqualTo(fixture.getId().toString());
        assertThat(executor.execute(request).outcome()).isEqualTo(io.github.yavonalabs.vectis.core.mutation.MutationResult.Outcome.INVALID);
        assertThat(reload().getSalary()).isEqualByComparingTo("60000");
    }

    @Test void auditFailureRollsBackEntityAndReceiptAndAllowsSameRequestRetry() {
        var input = input();
        VectisAuditLogService target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("Failure after audit flush"); }).when(target).recordMutation(any());
        assertThatThrownBy(() -> execute("managedRaise", input)).isInstanceOf(IllegalStateException.class);
        assertThat(reload().getSalary()).isEqualByComparingTo("60000");
        assertThat(audit.findByEntity("employee", fixture.getId().toString())).isEmpty();
        org.mockito.Mockito.reset(target);
        assertThat(execute("managedRaise", input).replayed()).isFalse();
        assertThat(reload().getSalary()).isEqualByComparingTo("60100");
    }

    @Test void noopStillConsumesVersionAndRejectsStaleAction() {
        execute("managedNoop", input());
        assertThat(reload().getVersion()).isGreaterThan(fixture.getVersion());
        assertThatThrownBy(() -> execute("managedRaise", input())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(reload().getSalary()).isEqualByComparingTo("60000");
    }

    @Test void outerTransactionCannotReceivePrematureCommittedResult() {
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> execute("managedRaise", input())))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(reload().getSalary()).isEqualByComparingTo("60000");
        assertThat(audit.findByEntity("employee", fixture.getId().toString())).isEmpty();
    }

    @Test void reviewedRequestRejectsMissingTamperedAndReusedProposalButAllowsExactReplay() {
        var input = new HashMap<>(input()); input.put("amount", "100");
        assertThatThrownBy(() -> execute("reviewedRaise", input)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        String proposal = proposals.issue("action-reviewer", "employee", "reviewedRaise", fixture.getId().toString(), fixture.getVersion().toString(), input);
        input.put("_proposal", proposal);
        var tampered = new HashMap<>(input); tampered.put("amount", "900");
        assertThatThrownBy(() -> execute("reviewedRaise", tampered)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(reload().getSalary()).isEqualByComparingTo("60000");
        execute("reviewedRaise", input);
        assertThat(execute("reviewedRaise", input).replayed()).isTrue();
        var reused = new HashMap<>(input); reused.put("_operation", UUID.randomUUID().toString());
        reused.put("_version", reload().getVersion().toString());
        assertThatThrownBy(() -> execute("reviewedRaise", reused)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(reload().getSalary()).isEqualByComparingTo("60100");
        assertThat(audit.findByEntity("employee", fixture.getId().toString())).hasSize(1);
    }

    @Test void expiredAndDifferentActorReviewsRejectWithoutEffect() {
        var input = new HashMap<>(input()); input.put("amount", "100");
        String wrongActor = proposals.issue("another-actor", "employee", "reviewedRaise", fixture.getId().toString(), fixture.getVersion().toString(), input);
        input.put("_proposal", wrongActor);
        assertThatThrownBy(() -> execute("reviewedRaise", input)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        String expired = proposals.issue("action-reviewer", "employee", "reviewedRaise", fixture.getId().toString(), fixture.getVersion().toString(), input);
        new TransactionTemplate(manager).executeWithoutResult(status -> em.createNativeQuery("update vectis_action_proposals set expires_at = :expiry where id = :id")
                .setParameter("expiry", java.time.Instant.now().minusSeconds(1)).setParameter("id", expired).executeUpdate());
        input.put("_proposal", expired);
        assertThatThrownBy(() -> execute("reviewedRaise", input)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(reload().getSalary()).isEqualByComparingTo("60000");
    }

    @Test void publicPreviewExecutionAndRecoveryUseSameActorAndOperation() throws Exception {
        String id = fixture.getId().toString(), key = UUID.randomUUID().toString();
        var preview = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/api/employee/action/toggleLeaveStatus/" + id + "/preview")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        var data = new com.fasterxml.jackson.databind.ObjectMapper().readTree(preview.getResponse().getContentAsString());
        assertThat(data.get("proposal").asText()).isNotBlank();
        var post = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/employee/action/toggleLeaveStatus/" + id)
                .param("_operation", key).param("_version", data.get("version").asText()).param("_proposal", data.get("proposal").asText())
                .param("_reason", "Public workflow").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf());
        mvc.perform(post).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
        mvc.perform(post).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
        assertThat(reload().getStatus()).isEqualTo(Employee.EmploymentStatus.ON_LEAVE);
        var logs = audit.findByEntity("employee", id);
        assertThat(logs).hasSize(1); assertThat(logs.get(0).getOperationId()).isEqualTo(key);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/api/operations/" + key))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.outcome").value("COMMITTED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/api/operations/" + key)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("other").roles("ADMIN")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/api/operations/" + key)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("action-reviewer").roles("USER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    @Test void concurrentVersionedActionsHaveOneCommittedWinner() throws Exception {
        race = new java.util.concurrent.CyclicBarrier(2);
        var a = new HashMap<>(input()); a.put("race", "yes");
        var b = new HashMap<>(input()); b.put("race", "yes");
        var results = concurrently(a, b);
        assertThat(results.stream().filter(ActionMutationService.Result.class::isInstance).count()).isEqualTo(1);
        assertThat(reload().getSalary()).isEqualByComparingTo("60100");
        assertThat(audit.findByEntity("employee", fixture.getId().toString())).hasSize(1);
    }

    @Test void concurrentDuplicateKeyDoesNotRepeatCommittedEffect() throws Exception {
        var input = input();
        concurrently(input, input);
        assertThat(execute("managedRaise", input).replayed()).isTrue();
        assertThat(reload().getSalary()).isEqualByComparingTo("60100");
        assertThat(audit.findByEntity("employee", fixture.getId().toString())).hasSize(1);
    }

    List<Object> concurrently(Map<String, String> a, Map<String, String> b) throws Exception {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Object>>();
            for (var input : List.of(a, b)) futures.add(pool.submit(() -> {
                var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                context.setAuthentication(auth); org.springframework.security.core.context.SecurityContextHolder.setContext(context);
                try { return execute("managedRaise", input); } catch (RuntimeException ex) { return ex; }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            }));
            return List.of(futures.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS), futures.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
