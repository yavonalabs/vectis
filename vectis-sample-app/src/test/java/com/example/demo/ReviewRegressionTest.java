package com.example.demo;

import com.example.demo.entity.Employee;
import io.github.yavonalabs.vectis.core.annotation.AdminAction;
import io.github.yavonalabs.vectis.core.annotation.ExternalApiCall;
import io.github.yavonalabs.vectis.core.context.DryRunProtectionAspect;
import io.github.yavonalabs.vectis.core.metadata.EntityMetadataRegistry;
import io.github.yavonalabs.vectis.core.query.DynamicCriteriaQueryEngine;
import io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regression coverage for the defects found during the initial review. */
@SpringBootTest(properties = "vectis.roles=ROLE_ADMIN,ROLE_USER")
@AutoConfigureMockMvc
@Import(ReviewRegressionTest.ProbeConfiguration.class)
class ReviewRegressionTest {
    @Test void staleNativeSaveRetainsInputAndOriginalVersion() throws Exception {
        String key = java.util.UUID.randomUUID().toString();
        String html = mvc.perform(post("/admin/employee/save").param("_operation", key)
                .param("__id", "1").param("version", "-1").param("firstName", "Unsaved work")
                .param("status", "ON_LEAVE").param("_reason", "Retain this reason")
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Unsaved work", "Retain this reason", key, "Your submitted values are retained");
        assertThat(html).containsPattern("name=\"version\"[^>]*value=\"-1\"");
        assertThat(html).containsPattern("value=\"ON_LEAVE\"[^>]*selected=\"selected\"");
        assertThat(employee(1).getFirstName()).isNotEqualTo("Unsaved work");
    }
    @Autowired MockMvc mvc;
    @Autowired DynamicCriteriaQueryEngine engine;
    @Autowired EntityMetadataRegistry registry;
    @Autowired ApplicationContext context;
    @Autowired AdminPermissionEvaluator permissions;
    @Autowired TransactionTemplate tx;
    @Autowired ProbeService probe;

    Employee employee(long id) { return engine.findById(registry.getBySlug("employee").orElseThrow(), id); }

    @Test void rootOpensWorkspaceAndLogoutInvalidatesSession() throws Exception {
        mvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/admin"));
        var session = new org.springframework.mock.web.MockHttpSession();
        mvc.perform(post("/logout").session(session).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login?logout"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/admin")).andExpect(status().isFound());
        mvc.perform(post("/logout").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test void relatedCollectionsAreBoundedAndDoNotInitializeSourceCollection() {
        tx.executeWithoutResult(transaction -> {
            try {
                var descriptor = registry.getBySlug("employee").orElseThrow();
                Employee employee = employee(1);
                employee.getSkills().clear();
                for (int i = 0; i < 31; i++) {
                    var skill = new com.example.demo.entity.Skill("Paged skill " + i);
                    engine.persist(skill);
                    employee.getSkills().add(skill);
                }
                engine.save(employee);
                var em = context.getBean(jakarta.persistence.EntityManager.class);
                em.flush(); em.clear();
                Employee unloaded = engine.findById(descriptor, 1L, false);
                assertThat(org.hibernate.Hibernate.isInitialized(unloaded.getSkills())).isFalse();
                var association = descriptor.associations().stream().filter(a -> a.name().equals("skills")).findFirst().orElseThrow();
                var target = registry.getBySlug("skill").orElseThrow();
                var first = engine.findRelatedPage(descriptor, 1L, association, target, 0);
                var second = engine.findRelatedPage(descriptor, 1L, association, target, 1);
                assertThat(first.content()).hasSize(25);
                assertThat(first.totalElements()).isEqualTo(31);
                assertThat(second.content()).hasSize(6).doesNotContainAnyElementsOf(first.content());
                assertThat(org.hibernate.Hibernate.isInitialized(unloaded.getSkills())).isFalse();
                mvc.perform(get("/admin/employee/view/1/related/skills").with(user("user").roles("USER")))
                        .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Next page")));
                mvc.perform(get("/admin/employee/view/1/related/skills").param("page", "1").with(user("admin").roles("ADMIN")))
                        .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Previous page")));
            } catch (Exception ex) { throw new RuntimeException(ex); }
            finally { transaction.setRollbackOnly(); }
        });
    }

    @Test void relatedCollectionEndpointsValidateSourceAssociationAndPage() throws Exception {
        for (String suffix : new String[]{"?page=-1", "?page=2147483647"})
            mvc.perform(get("/admin/employee/view/1/related/skills" + suffix).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isBadRequest());
        for (String path : new String[]{"/admin/employee/view/999999/related/skills", "/admin/employee/view/1/related/department",
                "/admin/employee/view/1/related/internalSecurityToken"})
            mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test void recordNavigationPreservesListContext() throws Exception {
        String query = "page=1&size=10&search=Alice&sort=email&dir=desc&filterField=status&filterOp=eq&filterValue=ACTIVE";
        for (String path : new String[]{"/admin/employee/view/1", "/admin/employee/edit/1", "/admin/employee/peek/1"}) {
            var result = mvc.perform(get(path).param("_list", query).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk()).andReturn();
            assertThat(result.getModelAndView().getModel().get("listUrl")).isEqualTo("/admin/employee?" + query);
            assertThat(result.getResponse().getContentAsString()).contains("/admin/employee?page=1&amp;size=10&amp;search=Alice");
        }
        var listing = mvc.perform(get("/admin/employee?" + query).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn();
        assertThat(listing.getResponse().getContentAsString()).contains("_list=page%3D1");
    }

    @Test void saveAndValidationKeepListContext() {
        tx.executeWithoutResult(transaction -> {
            try {
                String query = "search=Alice&sort=email&dir=desc";
                mvc.perform(post("/admin/employee/save").param("_operation", java.util.UUID.randomUUID().toString()).param("__id", "1").param("version", employee(1).getVersion().toString()).param("firstName", "Alice")
                        .param("_reason", "Navigation test").param("_list", query).with(user("admin").roles("ADMIN")).with(csrf()))
                        .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/employee?" + query));
                mvc.perform(post("/admin/employee/save").param("_operation", java.util.UUID.randomUUID().toString()).header("HX-Request", "true").param("__id", "1").param("version", employee(1).getVersion().toString())
                        .param("firstName", "Alice").param("_reason", "Navigation test").param("_list", query)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                        .andExpect(status().isOk()).andExpect(header().string("HX-Redirect", "/admin/employee/view/1?_list=search%3DAlice%26sort%3Demail%26dir%3Ddesc"));
                var invalid = mvc.perform(post("/admin/employee/save").param("_operation", java.util.UUID.randomUUID().toString()).header("HX-Request", "true").param("__id", "1").param("version", employee(1).getVersion().toString())
                        .param("firstName", "").param("_reason", "Navigation test").param("_list", query)
                        .with(user("admin").roles("ADMIN")).with(csrf())).andReturn();
                assertThat(invalid.getResponse().getContentAsString()).contains("name=\"_list\"", "search=Alice&amp;sort=email&amp;dir=desc");
            } catch (Exception ex) { throw new RuntimeException(ex); }
            finally { transaction.setRollbackOnly(); }
        });
    }

    @Test void expiredHtmxSessionRedirectsTheWholePage() throws Exception {
        mvc.perform(get("/admin/employee").header("HX-Request", "true"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("HX-Redirect", "/login?expired"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().string(""));
        mvc.perform(post("/admin/employee/save").param("_operation", java.util.UUID.randomUUID().toString()).header("HX-Request", "true").param("firstName", "Never saved"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("HX-Redirect", "/login?expired"));
        mvc.perform(post("/admin/employee/save").param("_operation", java.util.UUID.randomUUID().toString()).header("HX-Request", "true").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("HX-Redirect"));
        mvc.perform(get("/login?expired")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Your session has ended")));
    }

    @Test void anonymousDashboardRequiresLogin() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection());
    }
    @Test void csrfIsRequiredForMutation() throws Exception {
        mvc.perform(post("/admin/employee/action/toggleLeaveStatus/1").with(user("admin").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }
    @Test void listDetailEditCreateAndAuditRender() throws Exception {
        for (String path : new String[]{"/admin", "/admin/employee", "/admin/employee/view/1", "/admin/employee/edit/1", "/admin/employee/create", "/admin/audit", "/admin/department", "/admin/skill"}) {
            mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        }
    }
    @Test void searchAndSortingWork() {
        var descriptor = registry.getBySlug("employee").orElseThrow();
        var result = engine.<Employee>findPage(descriptor, 0, 2, "ALICE", "salary", "desc");
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getFirstName()).isEqualTo("Alice");
        var sorted = engine.<Employee>findPage(descriptor, 0, 2, null, "salary", "desc");
        assertThat(sorted.content().get(0).getFirstName()).isEqualTo("Diana");
    }
    @Test void ignoredFieldsAreAbsentFromListAndForm() throws Exception {
        for (String path : new String[]{"/admin/employee", "/admin/employee/edit/1"}) {
            var result = mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andReturn();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("SECRET_HASH_492", "internalSecurityToken");
        }
    }
    @Test void previewRollsBackSalary() throws Exception {
        var before = employee(3).getSalary();
        mvc.perform(post("/admin/api/employee/action/promoteEmployee/3/preview").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hasChanges").value(true));
        assertThat(employee(3).getSalary()).isEqualByComparingTo(before);
    }
    @Test void regularUserCannotExecuteAdminPromotion() {
        tx.executeWithoutResult(s -> {
            try {
                mvc.perform(post("/admin/employee/action/promoteEmployee/3").with(user("user").roles("USER")).with(csrf()))
                    .andExpect(status().isForbidden());
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { s.setRollbackOnly(); }
        });
    }
    @Test void regularUserCannotPreviewAdminPromotion() throws Exception {
        mvc.perform(post("/admin/api/employee/action/promoteEmployee/3/preview").with(user("user").roles("USER")).with(csrf()))
            .andExpect(status().isForbidden());
    }
    @Test void globalSearchEndpointExists() throws Exception {
        mvc.perform(get("/admin/api/search").param("q", "Alice").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk());
    }
    @Test void previewNeverDisclosesIgnoredFieldChanges() throws Exception {
        var result = mvc.perform(post("/admin/api/employee/action/rotateReviewToken/3/preview").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SECRET_HASH_492", "REVIEW_NEW_TOKEN", "internalSecurityToken");
    }
    @Test void dryRunProtectionIsRegistered() {
        assertThat(context.getBeansOfType(DryRunProtectionAspect.class)).isNotEmpty();
    }
    @Test void previewBlocksAnnotatedExternalCalls() throws Exception {
        probe.reset();
        mvc.perform(post("/admin/api/employee/action/reviewExternalEffect/3/preview").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isUnprocessableEntity());
        assertThat(probe.count()).isZero();
    }
    @Test void anonymousTokenDoesNotGrantAdminAccess() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("review", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        try { assertThat(permissions.canAccessAdmin(null)).isFalse(); }
        finally { SecurityContextHolder.clearContext(); }
    }
    @Test void invalidSalaryRejectedAndDataUnchanged() throws Exception {
        var before = employee(3).getSalary();
        mvc.perform(post("/admin/employee/save").param("_operation", java.util.UUID.randomUUID().toString()).param("__id", "3").param("version", employee(3).getVersion().toString()).param("salary", "1").param("_reason", "Validation test")
            .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(model().attributeExists("errorMessage"));
        assertThat(employee(3).getSalary()).isEqualByComparingTo(before);
    }
    @Test void zeroPageSizeIsRejected() throws Exception {
        mvc.perform(get("/admin/employee").param("size", "0").with(user("admin").roles("ADMIN")))
            .andExpect(status().isBadRequest());
    }
    @Test void missingRecordReturns404() throws Exception {
        mvc.perform(get("/admin/employee/view/99999").with(user("admin").roles("ADMIN")))
            .andExpect(status().isNotFound());
    }
    @Test void terminationPreviewPreservesSalaryAndProjectsOnlyStatus() throws Exception {
        var salary = employee(3).getSalary();
        mvc.perform(post("/admin/api/employee/action/terminateEmployee/3/preview").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plainTextChanges.length()").value(1))
            .andExpect(jsonPath("$.plainTextChanges[0]").value("Status changed from On leave to Terminated"));
        assertThat(employee(3).getSalary()).isEqualByComparingTo(salary);
    }

    @Test void operatorTablePrioritizesNamesAndHidesTechnicalColumns() throws Exception {
        String html = mvc.perform(get("/admin/employee").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html.substring(html.indexOf("<table"), html.indexOf("</table>"))).contains("Alice Vance", "On leave", "vx-record-link")
                .doesNotContain("int8 [pk]", "varchar", "fk &rarr;", ">Version<", ">Salary<");
        assertThat(registry.getBySlug("employee").orElseThrow().listFields())
                .extracting(f -> f.name()).containsExactly("email", "status");
    }

    @Test void invalidFormShowsFieldErrorAndRetainsInput() throws Exception {
        String html = mvc.perform(post("/admin/employee/save").param("_operation", java.util.UUID.randomUUID().toString()).param("__id", "3").param("version", employee(3).getVersion().toString())
                .param("firstName", "   ").param("lastName", "Retained")
                .param("_reason", "Keep my explanation").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("fieldErrors"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("firstName-error", "aria-invalid=\"true\"", "Retained", "Keep my explanation");
        assertThat(employee(3).getFirstName()).isEqualTo("Carlos");
    }

    @Test void terminationExecutionRetainsRecordedSalary() {
        tx.executeWithoutResult(transaction -> {
            try {
                var salary = employee(3).getSalary();
                mvc.perform(post("/admin/employee/action/terminateEmployee/3")
                        .param("_reason", "End employment record")
                        .param("_version", employee(3).getVersion().toString())
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                        .andExpect(status().is3xxRedirection());
                assertThat(employee(3).getStatus()).isEqualTo(Employee.EmploymentStatus.TERMINATED);
                assertThat(employee(3).getSalary()).isEqualByComparingTo(salary);
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { transaction.setRollbackOnly(); }
        });
    }

    @Test void typedStatusAndSalaryFiltersCombineWithTextSearch() throws Exception {
        var result = mvc.perform(get("/admin/employee").param("search", "Carlos")
                .param("filterField", "status", "salary").param("filterOp", "eq", "gte")
                .param("filterValue", "ON_LEAVE", "90000")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andReturn();
        var page = (io.github.yavonalabs.vectis.core.query.PageResult<?>) result.getModelAndView().getModel().get("pageResult");
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(((Employee) page.content().get(0)).getFirstName()).isEqualTo("Carlos");
        String html = result.getResponse().getContentAsString();
        assertThat(html).contains("filterField=status", "filterField=salary", "filterOp=gte", "filterValue=90000");
    }

    @Test void typedStatusIsNotMatchedByUnrelatedText() throws Exception {
        var result = mvc.perform(get("/admin/employee").param("search", "Alice")
                .param("filterField", "status").param("filterOp", "eq").param("filterValue", "ON_LEAVE")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andReturn();
        var page = (io.github.yavonalabs.vectis.core.query.PageResult<?>) result.getModelAndView().getModel().get("pageResult");
        assertThat(page.totalElements()).isZero();
    }

    @Test void invalidFiltersFailWithoutReturningUnfilteredData() throws Exception {
        for (String[] condition : new String[][]{{"internalSecurityToken","eq","secret"},
                {"status","contains","ACTIVE"}, {"salary","gt","NaN"}, {"status","eq","NOT_A_STATUS"},
                {"salary","eq","999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999"}}) {
            var result = mvc.perform(get("/admin/employee").header("HX-Request", "true")
                    .param("filterField", condition[0]).param("filterOp", condition[1]).param("filterValue", condition[2])
                    .with(user("admin").roles("ADMIN")))
                    .andExpect(status().isBadRequest()).andExpect(header().string("X-Vectis-Filter-Error", "true")).andReturn();
            assertThat(result.getResponse().getContentAsString()).contains("filter-error", "Review filters", "Filters need attention", "Clear search and filters").doesNotContain(" active)", "Alice Vance", "SECRET_HASH_492");
        }
    }

    @Test void filterShapeAndWildcardSearchAreBounded() throws Exception {
        mvc.perform(get("/admin/employee").param("filterField", "status")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/employee").param("filterField", "status", "status", "status", "status")
                .param("filterOp", "eq", "eq", "eq", "eq").param("filterValue", "ACTIVE", "ACTIVE", "ACTIVE", "ACTIVE")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        var page = engine.findPage(registry.getBySlug("employee").orElseThrow(), 0, 10, "%", null, "asc");
        assertThat(page.totalElements()).isZero();
    }

    @Test void relationshipOptionsArePagedAndKeepOffPageSelection() {
        tx.executeWithoutResult(transaction -> {
            try {
                com.example.demo.entity.Department last = null;
                for (int i = 0; i < 31; i++) {
                    last = new com.example.demo.entity.Department("Lookup fixture " + i, "T");
                    engine.persist(last);
                }
                Employee employee = employee(3);
                employee.setDepartment(last);
                engine.save(employee);
                mvc.perform(get("/admin/employee/relationships/department/options").with(user("admin").roles("ADMIN")))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(25)).andExpect(jsonPath("$.hasNext").value(true));
                mvc.perform(get("/admin/employee/relationships/department/options").param("search", "Lookup fixture 30")
                        .with(user("admin").roles("ADMIN")))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                        .andExpect(jsonPath("$.items[0].label").value("Lookup fixture 30"));
                var result = mvc.perform(get("/admin/employee/edit/3").with(user("admin").roles("ADMIN")))
                        .andExpect(status().isOk()).andReturn();
                @SuppressWarnings("unchecked")
                var options = (java.util.Map<String, java.util.List<java.util.Map<String, String>>>) result.getModelAndView().getModel().get("formOptions");
                assertThat(options.get("department")).hasSize(26).anySatisfy(option -> {
                    assertThat(option.get("label")).isEqualTo("Lookup fixture 30");
                    assertThat(option.get("selected")).isEqualTo("true");
                });
            } catch (Exception ex) { throw new RuntimeException(ex); }
            finally { transaction.setRollbackOnly(); }
        });
    }

    @Test void htmxValidationReturnsOneFormFragmentWithAccessibleErrors() throws Exception {
        String html = mvc.perform(post("/admin/employee/save").param("_operation", java.util.UUID.randomUUID().toString()).header("HX-Request", "true")
                .param("__id", "3").param("version", employee(3).getVersion().toString()).param("firstName", " ").param("_reason", "Check correction flow")
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("vectis/fragments/edit-form :: editFormFragment"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("firstName-error", "href=\"#firstName\"", "Check correction flow")
                .doesNotContain("<html", "id=\"sidebar\"");
    }
    @Test void mutationsRequireNonblankReason() {
        tx.executeWithoutResult(s -> {
            try {
                mvc.perform(post("/admin/employee/action/toggleLeaveStatus/3").param("_reason", " ")
                    .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { s.setRollbackOnly(); }
        });
    }
    @Test void createUpdateAuditAndDeleteWork() throws Exception {
        String email = "review-probe@example.test";
        mvc.perform(post("/admin/employee/save").param("_operation", java.util.UUID.randomUUID().toString()).param("firstName", "Review").param("lastName", "Probe").param("email", email)
            .param("salary", "60000").param("status", "ACTIVE").param("department", "1").param("_reason", "Review test")
            .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().is3xxRedirection());
        var descriptor = registry.getBySlug("employee").orElseThrow();
        var created = engine.<Employee>findPage(descriptor, 0, 10, email, null, null).content().get(0);
        try {
            mvc.perform(post("/admin/employee/save").param("_operation", java.util.UUID.randomUUID().toString()).param("__id", created.getId().toString()).param("firstName", "Updated").param("_reason", "Update test")
                .param("version", created.getVersion().toString()).param("department", "1")
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().is3xxRedirection());
            assertThat(employee(created.getId()).getFirstName()).isEqualTo("Updated");
            var audits = context.getBean(io.github.yavonalabs.vectis.core.audit.VectisAuditLogService.class).findByEntity("employee", created.getId().toString());
            assertThat(audits).hasSize(2);
        } finally {
            mvc.perform(post("/admin/employee/delete/" + created.getId()).param("_operation", java.util.UUID.randomUUID().toString()).param("_version", employee(created.getId()).getVersion().toString()).param("_reason", "Remove test fixture").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        }
        assertThat(employee(created.getId())).isNull();
    }

    @Test void htmxSaveReturnsNavigationToSavedRecord() throws Exception {
        var result = mvc.perform(post("/admin/employee/save").param("_operation", java.util.UUID.randomUUID().toString()).header("HX-Request", "true")
            .param("__id", "3").param("version", employee(3).getVersion().toString()).param("firstName", "Carlos").param("_reason", "Browser navigation regression")
            .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(header().string("HX-Redirect", "/admin/employee/view/3")).andReturn();
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test void previewIsNeverTheExecutionHandler() throws Exception {
        probe.reset();
        mvc.perform(post("/admin/api/employee/action/unsafeUnannotatedEffect/3/preview")
            .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isUnprocessableEntity());
        assertThat(probe.count()).isZero();
    }

    @Test void globalSearchReturnsRecordLabelAndUrl() throws Exception {
        mvc.perform(get("/admin/api/search").param("q", "Alice").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].items[0].title").value("Alice Vance"))
            .andExpect(jsonPath("$[0].items[0].url").value("/admin/employee/view/1"));
    }

    @Test void oversizedAndNegativePaginationAreRejected() throws Exception {
        for (String size : new String[]{"-1", "101", "2147483647"}) {
            mvc.perform(get("/admin/employee").param("size", size).with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/admin/employee").param("page", "-1").with(user("admin").roles("ADMIN")))
            .andExpect(status().isBadRequest());
    }

    @Test void failedActionAfterExternalEffectDoesNotInviteBlindRetry() throws Exception {
        probe.reset();
        mvc.perform(post("/admin/employee/action/effectThenFailure/3")
                .param("_version", employee(3).getVersion().toString())
                .param("_reason", "Ambiguous outcome regression")
                .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", org.hamcrest.Matchers.containsString("external effect may already have occurred")))
                .andExpect(flash().attributeCount(1));
        assertThat(probe.count()).isEqualTo(1);
    }

    @Test void actionRejectsStalePreviewVersion() throws Exception {
        mvc.perform(post("/admin/employee/action/promoteEmployee/3").param("_reason", "Conflict test")
            .param("_version", "-1").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isConflict());
    }

    @Test void missingOrBlankActionVersionCannotInvokeHandler() throws Exception {
        probe.reset();
        mvc.perform(post("/admin/employee/action/effectThenFailure/3").param("_reason", "Missing version")
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(post("/admin/employee/action/effectThenFailure/3").param("_reason", "Blank version").param("_version", " ")
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(post("/admin/employee/action/effectThenFailure/3").param("_reason", "Invalid version").param("_version", "invalid")
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
        assertThat(probe.count()).isZero();
    }

    @Test void invalidIdentifierIsABadRequest() throws Exception {
        mvc.perform(get("/admin/employee/view/not-a-number").with(user("admin").roles("ADMIN")))
            .andExpect(status().isBadRequest());
    }

    @Test void externalCallInExplicitPreviewFailsClosed() throws Exception {
        probe.reset();
        mvc.perform(post("/admin/api/employee/action/badExplicitPreview/3/preview")
            .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isInternalServerError());
        assertThat(probe.count()).isZero();
    }

    @TestConfiguration static class ProbeConfiguration {
        @Bean ProbeService probeService() { return new ProbeService(); }
        @Bean ReviewActions reviewActions(ProbeService probe) { return new ReviewActions(probe); }
    }
    @Service @org.springframework.boot.test.context.TestComponent static class ProbeService {
        final AtomicInteger calls = new AtomicInteger();
        public void reset() { calls.set(0); }
        public int count() { return calls.get(); }
        public void unannotatedCall() { calls.incrementAndGet(); }
        @ExternalApiCall public void externalCall() { calls.incrementAndGet(); }
    }
    @Service @org.springframework.boot.test.context.TestComponent static class ReviewActions {
        final ProbeService probe;
        ReviewActions(ProbeService probe) { this.probe = probe; }
        @AdminAction(previewMethod = "previewToken") public void rotateReviewToken(Employee employee) { employee.setInternalSecurityToken("REVIEW_NEW_TOKEN"); }
        public java.util.Map<String, Object> previewToken(Employee employee) { return java.util.Map.of("internalSecurityToken", "REVIEW_NEW_TOKEN"); }
        @AdminAction public void unsafeUnannotatedEffect(Employee employee) { probe.unannotatedCall(); }
        @AdminAction public void effectThenFailure(Employee employee) {
            probe.unannotatedCall();
            throw new IllegalStateException("Simulated failure after external dispatch");
        }
        @AdminAction(previewMethod = "invalidPreview") public void badExplicitPreview(Employee employee) { probe.unannotatedCall(); }
        public java.util.Map<String, Object> invalidPreview(Employee employee) { probe.externalCall(); return java.util.Map.of(); }
        @AdminAction public void reviewExternalEffect(Employee employee) { probe.externalCall(); }
    }
}
