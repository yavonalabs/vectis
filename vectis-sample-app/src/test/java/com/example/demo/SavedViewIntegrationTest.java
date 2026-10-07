package com.example.demo;

import io.github.yavonalabs.vectis.core.view.SavedViewService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${VECTIS_TEST_DB_URL:jdbc:h2:mem:savedviews;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=${VECTIS_TEST_DB_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${VECTIS_TEST_DB_USER:sa}",
        "spring.datasource.password=${VECTIS_TEST_DB_PASSWORD:}"})
@AutoConfigureMockMvc
@Transactional
class SavedViewIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired SavedViewService views;
    private String create(String owner, String state) throws Exception {
        String location = mvc.perform(post("/admin/employee/saved-views").with(user(owner).roles("USER")).with(csrf())
                .param("name", "My team").param("state", state)).andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return location.substring(location.lastIndexOf('/') + 1);
    }
    @Test void readOnlyUserCanSaveOpenAndRemoveTheirOwnView() throws Exception {
        String id = create("reader", "search=Alice&size=25&sort=email&dir=desc&page=3&filterField=status&filterOp=eq&filterValue=ACTIVE");
        mvc.perform(get("/admin/employee/saved-views/" + id).with(user("reader").roles("USER")))
                .andExpect(redirectedUrl("/admin/employee?search=Alice&size=25&sort=email&dir=desc&filterField=status&filterOp=eq&filterValue=ACTIVE"));
        mvc.perform(get("/admin/employee").with(user("reader").roles("USER")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("My team")));
        mvc.perform(post("/admin/employee/saved-views/" + id + "/delete").with(user("reader").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/admin/employee"));
        mvc.perform(get("/admin/employee/saved-views/" + id).with(user("reader").roles("USER"))).andExpect(status().isNotFound());
    }
    @Test void OtherAccountsCannotListOpenOrDeleteEvenAsAdmin() throws Exception {
        String id = create("reader", "search=Alice");
        mvc.perform(get("/admin/employee").with(user("other").roles("ADMIN")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(id))));
        mvc.perform(get("/admin/employee/saved-views/" + id).with(user("other").roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(post("/admin/employee/saved-views/" + id + "/delete").with(user("other").roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/admin/department/saved-views/" + id).with(user("reader").roles("USER"))).andExpect(status().isNotFound());
    }
    @Test void entityPermissionIsRecheckedAndOwnerCannotBeSupplied() throws Exception {
        String location = mvc.perform(post("/admin/department/saved-views").with(user("reader").roles("USER")).with(csrf())
                .param("name", "Departments").param("owner", "admin")).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(get(location).with(user("reader").roles("RESTRICTED"))).andExpect(status().isForbidden());
        mvc.perform(get(location).with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(post(location + "/delete").with(user("reader").roles("RESTRICTED")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get(location).with(user("reader").roles("USER"))).andExpect(status().is3xxRedirection());
    }
    @Test void csrfAndDirectServiceAuthenticationAreRequired() throws Exception {
        mvc.perform(post("/admin/employee/saved-views").with(user("reader").roles("USER")).param("name", "No token")).andExpect(status().isForbidden());
        String id = create("reader", "");
        mvc.perform(post("/admin/employee/saved-views/" + id + "/delete").with(user("reader").roles("USER"))).andExpect(status().isForbidden());
        assertThatThrownBy(() -> views.list("employee")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void rejectsMalformedOrOversizedStateInsteadOfBroadeningResults() throws Exception {
        mvc.perform(post("/admin/employee/saved-views").with(user("reader").roles("USER")).with(csrf())).andExpect(status().isBadRequest());
        for (String state : java.util.List.of("sort=password", "size=10000", "search=a&search=b", "redirect=https%3A%2F%2Fevil.example", "search=%zz",
                "filterField=salary&filterOp=contains&filterValue=2", "filterField=missing&filterOp=eq&filterValue=x",
                "filterField=email&filterOp=eq", "search=" + "a".repeat(201), "search=%0A", "page=-1", "x=" + "a".repeat(8192))) {
            mvc.perform(post("/admin/employee/saved-views").with(user("reader").roles("USER")).with(csrf())
                    .param("name", "Invalid").param("state", state)).andExpect(status().isBadRequest());
        }
        assertThat(em.createQuery("select count(v) from SavedView v", Long.class).getSingleResult()).isZero();
    }
    @Test void oldSchemaAndRemovedFieldsFailClosedButRemainDeletable() throws Exception {
        String id = create("reader", "search=Alice");
        em.createQuery("update SavedView v set v.queryState='sort=removedField' where v.id=:id").setParameter("id", id).executeUpdate(); em.clear();
        mvc.perform(get("/admin/employee/saved-views/" + id).with(user("reader").roles("USER"))).andExpect(status().isBadRequest());
        em.createQuery("update SavedView v set v.schemaVersion=99 where v.id=:id").setParameter("id", id).executeUpdate(); em.clear();
        mvc.perform(get("/admin/employee/saved-views/" + id).with(user("reader").roles("USER"))).andExpect(status().isConflict());
        mvc.perform(post("/admin/employee/saved-views/" + id + "/delete").with(user("reader").roles("USER")).with(csrf())).andExpect(status().is3xxRedirection());
    }
    @Test void quotaCanBeReusedAfterDeletingAView() throws Exception {
        String first = create("reader", "");
        for (int i=1; i<50; i++) create("reader", "");
        mvc.perform(post("/admin/employee/saved-views").with(user("reader").roles("USER")).with(csrf()).param("name", "Overflow"))
                .andExpect(status().isConflict());
        mvc.perform(post("/admin/employee/saved-views/" + first + "/delete").with(user("reader").roles("USER")).with(csrf())).andExpect(status().is3xxRedirection());
        create("reader", "search=Bob");
    }
    @Test void freshEditFormIsNotMarkedDirty() throws Exception {
        mvc.perform(get("/admin/employee/edit/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("data-unsaved=\"false\"")));
    }
    @Test void appliedStateIsCapturedFromValidatedListParameters() throws Exception {
        mvc.perform(get("/admin/employee").with(user("reader").roles("USER"))
                .param("search", "Alice").param("size", "25").param("ignored", "x".repeat(9000)))
                .andExpect(status().isOk()).andExpect(model().attribute("savedViewState", "search=Alice&size=25"));
        mvc.perform(get("/admin/employee").with(user("reader").roles("USER")).param("size", "20"))
                .andExpect(status().isOk()).andExpect(model().attributeExists("savedViewError"));
    }
}
