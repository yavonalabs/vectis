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
