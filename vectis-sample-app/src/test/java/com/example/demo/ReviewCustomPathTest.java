package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"vectis.path=/ops", "spring.datasource.url=jdbc:h2:mem:reviewpath;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class ReviewCustomPathTest {
    @Autowired MockMvc mvc;
    @Test void personalViewsRespectContextPathAndPreserveFeedback() throws Exception {
        var created = mvc.perform(post("/portal/ops/employee/saved-views").contextPath("/portal")
                .with(user("path-reader").roles("USER")).with(csrf()).param("name", "My view").param("state", "search=Alice"))
                .andExpect(status().isFound()).andExpect(flash().attribute("flashMessage", "Personal view saved."))
                .andReturn();
        String target = created.getResponse().getRedirectedUrl();
        assertThat(target).startsWith("/portal/ops/employee/saved-views/");
        mvc.perform(get(target).contextPath("/portal").with(user("path-reader").roles("USER"))
                .flashAttrs(created.getFlashMap()))
                .andExpect(redirectedUrl("/portal/ops/employee?search=Alice"))
                .andExpect(flash().attribute("flashMessage", "Personal view saved."));
        mvc.perform(post(target + "/delete").contextPath("/portal").with(user("path-reader").roles("USER")).with(csrf()))
                .andExpect(redirectedUrl("/portal/ops/employee"));
    }
    @Test void rootAndLogoutRespectConfiguredPaths() throws Exception {
        mvc.perform(get("/portal/").contextPath("/portal"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/portal/ops"));
        String html = mvc.perform(get("/portal/ops").contextPath("/portal").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("action=\"/portal/logout\"", "Log out", "name=\"_csrf\"");
    }
    @Test void expiredSessionRedirectIncludesContextPath() throws Exception {
        mvc.perform(get("/portal/ops/employee").contextPath("/portal").header("HX-Request", "true"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("HX-Redirect", "/portal/login?expired"));
    }

    @Test void listNavigationWorksWithContextPath() throws Exception {
        var result = mvc.perform(get("/portal/ops/employee/edit/1").contextPath("/portal")
                .param("_list", "search=Alice&page=2").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("href=\"/portal/ops/employee?search=Alice&amp;page=2\"")
                .doesNotContain("/portal/portal");
    }

    @Test void relatedCollectionLinksRespectContextAndAdminPath() throws Exception {
        var result = mvc.perform(get("/portal/ops/employee/view/1/related/skills").contextPath("/portal")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("/portal/ops/employee/view/1", "/portal/ops/skill/view/")
                .doesNotContain("/portal/portal");
    }

    @Test void contextPathIsIncludedExactlyOnceInLinksAndSearch() throws Exception {
        var result = mvc.perform(get("/portal/ops").contextPath("/portal").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("href=\"/portal/ops/employee\"", "data-admin-base=\"/portal/ops\"")
            .doesNotContain("/portal/portal");
        mvc.perform(get("/portal/ops/api/search").contextPath("/portal").param("q", "Alice").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].items[0].url").value("/portal/ops/employee/view/1"));
    }
    @Test void customPathLinksStayInsideConfiguredPath() throws Exception {
        var result = mvc.perform(get("/ops").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("href=\"/ops/employee\"")
            .doesNotContain("href=\"/admin/employee\"");
    }
    @Test void customPathPreviewEndpointExists() throws Exception {
        mvc.perform(post("/ops/api/employee/action/promoteEmployee/3/preview").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
    }

    @Test void filtersAndRelationshipLookupRespectContextPath() throws Exception {
        var result = mvc.perform(get("/portal/ops/employee").contextPath("/portal")
                .param("filterField", "status").param("filterOp", "eq").param("filterValue", "ACTIVE")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("/portal/ops/employee?sort=email", "filterValue=ACTIVE")
                .doesNotContain("/portal/portal");
        mvc.perform(get("/portal/ops/employee/relationships/department/options").contextPath("/portal")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].label").value("Engineering"));
    }
}
