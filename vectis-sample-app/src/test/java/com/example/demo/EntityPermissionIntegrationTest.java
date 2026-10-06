package com.example.demo;

import io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:entitypermissions;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class EntityPermissionIntegrationTest {
    @Autowired MockMvc mvc;
    @SpyBean AdminPermissionEvaluator permissions;
    @Autowired io.github.yavonalabs.vectis.core.mutation.ActionMutationService mutations;
    @Autowired io.github.yavonalabs.vectis.core.mutation.RecordMutationService records;

    @Test void directRecordServiceRejectsUnauthenticatedWrites() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> records.save("employee", java.util.Map.of("actor", "admin")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(403));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> records.delete("employee", "1", java.util.Map.of("actor", "admin")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(403));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "restricted", roles = "RESTRICTED")
    void directRecordServiceRejectsRestrictedWrites() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> records.save("employee", java.util.Map.of("_reason", "Denied write")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(403));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> records.delete("employee", "1", java.util.Map.of("_reason", "Denied write")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(403));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
    void directRecordServicePreservesRelationshipDenial() {
        doReturn(false).when(permissions).canViewEntity(eq("department"), any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> records.save("employee",
                java.util.Map.of("__id", "1", "department", "1", "_reason", "Denied relationship")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(403));
    }

    @Test void directActionServiceRejectsMissingAuthentication() {
        assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()).isNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mutations.execute("employee", "toggleLeaveStatus", "1",
                java.util.Map.of("_reason", "Direct call", "actor", "admin")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(403));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "restricted", roles = "RESTRICTED")
    void directActionServiceRejectsRestrictedActorDespiteForgedInput() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mutations.execute("employee", "toggleLeaveStatus", "1",
                java.util.Map.of("_reason", "Direct call", "actor", "admin")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(403));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
    void directActionServiceRechecksEntityPermission() {
        doReturn(false).when(permissions).canViewEntity(eq("employee"), any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mutations.execute("employee", "toggleLeaveStatus", "1",
                java.util.Map.of("_reason", "Direct call")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(403));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
    void directActionServiceStillRequiresReasonAndChecksVersion() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mutations.execute("employee", "toggleLeaveStatus", "1", java.util.Map.of()))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mutations.execute("employee", "toggleLeaveStatus", "1",
                java.util.Map.of("_reason", "Direct call", "_version", "-1")))
                .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }

    @Test void realRestrictedAccountCanBrowseEmployeesButNotRelatedEntities() throws Exception {
        // No mocked permission decisions: exercise the sample's real role policy.
        for (String path : new String[]{"/admin/employee", "/admin/employee/view/1", "/admin/employee/peek/1"}) {
            String html = mvc.perform(get(path).with(user("restricted").roles("RESTRICTED")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).doesNotContain("Engineering");
        }
        String detail = mvc.perform(get("/admin/employee/view/1").with(user("restricted").roles("RESTRICTED")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("Related records unavailable.");
        for (String path : new String[]{"/admin/department", "/admin/department/view/1",
                "/admin/department/peek/1", "/admin/skill", "/admin/employee/view/1/related/skills",
                "/admin/employee/relationships/department/options", "/admin/employee/edit/1", "/admin/audit"}) {
            mvc.perform(get(path).with(user("restricted").roles("RESTRICTED")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/admin/api/search").param("q", "Engineering")
                .with(user("restricted").roles("RESTRICTED")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/admin/api/employee/action/toggleLeaveStatus/1/preview")
                .with(user("restricted").roles("RESTRICTED")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test void restrictedActivityIsUnavailableRatherThanFalselyEmpty() throws Exception {
        for (String path : new String[]{"/admin", "/admin/employee/view/1", "/admin/employee/peek/1"}) {
            String restricted = mvc.perform(get(path).with(user("user").roles("USER")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(restricted).contains("Activity history is unavailable for your account.")
                    .doesNotContain("No activity recorded yet.", "No operational activity recorded yet.");
            String permitted = mvc.perform(get(path).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(permitted).doesNotContain("Activity history is unavailable for your account.");
        }
    }

    @Test void collectionPagesRequireSourceAndTargetViewPermission() throws Exception {
        doReturn(false).when(permissions).canViewEntity(eq("skill"), any());
        mvc.perform(get("/admin/employee/view/1/related/skills").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        doReturn(true).when(permissions).canViewEntity(eq("skill"), any());
        doReturn(false).when(permissions).canViewEntity(eq("employee"), any());
        mvc.perform(get("/admin/employee/view/1/related/skills").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test void deniedEntityCannotLeakThroughSearchRelationshipsOrEdits() throws Exception {
        doReturn(false).when(permissions).canViewEntity(eq("department"), any());
        for (String path : new String[]{"/admin/employee", "/admin/employee/view/1", "/admin/employee/edit/1"}) {
            var result = mvc.perform(get(path).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("Engineering", "name=\"department\"");
        }
        mvc.perform(get("/admin/api/search").param("q", "Engineering").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/admin/employee/relationships/department/options").with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/admin/employee/relationships/department/options").with(user("user").roles("USER")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/admin/employee/save").param("__id", "1").param("department", "")
            .param("_reason", "Denied relationship change").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isForbidden());
    }
}
