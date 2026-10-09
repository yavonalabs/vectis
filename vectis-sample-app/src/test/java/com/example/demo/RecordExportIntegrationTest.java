package com.example.demo;

import com.example.demo.entity.Employee;
import io.github.yavonalabs.vectis.core.export.RecordExportService;
import io.github.yavonalabs.vectis.core.query.DynamicCriteriaQueryEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:exports;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class RecordExportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired RecordExportService exports;
    @Autowired DynamicCriteriaQueryEngine queries;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test void exportPermissionIsSeparateAndDirectCallsRequireAuthentication() throws Exception {
        for (String role : List.of("USER", "RESTRICTED")) mvc.perform(get("/admin/employee/export.csv").param("column", "email").with(user("reader").roles(role)))
                .andExpect(status().isForbidden());
        assertThatThrownBy(() -> exports.export("employee", Map.of("column", List.of("email"))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(new io.github.yavonalabs.vectis.core.security.AllowAllPermissionEvaluator().canExportEntity("employee", () -> "admin")).isFalse();
    }

    @Test void appliedFiltersAndOnlySelectedVisibleColumnsAreExported() throws Exception {
        String csv = mvc.perform(get("/admin/employee/export.csv").param("column", "firstName", "email")
                .param("filterField", "firstName").param("filterOp", "eq").param("filterValue", "Alice")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("Alice").doesNotContain("Bob", "SECRET_HASH", "Salary", "Engineering");
        for (String column : List.of("internalSecurityToken", "department", "version")) mvc.perform(get("/admin/employee/export.csv")
                .param("column", column).with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test void csvQuotesAndFormulaNeutralizationSurviveDatabaseProjection() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        queries.persist(new Employee("=1+1", "Comma,Quote\"Line\n", email, new BigDecimal("60000"), null));
        String csv = mvc.perform(get("/admin/employee/export.csv").param("column", "firstName", "lastName").param("search", email)
                .with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("\"'=1+1\"", "\"Comma,Quote\"\"Line\n\"");
        for (String attack : List.of("+SUM(A1)", "-1+1", "@SUM(A1)", "  =1", "\t=1", "\r=1"))
            assertThat(RecordExportService.cell(attack)).startsWith("\"'");
    }

    @Test void missingColumnsAndOversizedFiltersDoNotProduceAnAttachment() throws Exception {
        mvc.perform(get("/admin/employee/export.csv").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Content-Disposition"));
        mvc.perform(get("/admin/employee/export.csv").param("column", "email").param("search", "x".repeat(201))
                .with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest()).andExpect(header().doesNotExist("Content-Disposition"));
    }

    @Test void rowCellAndUtf8ByteLimitsRejectWithoutPartialDownload() throws Exception {
        jdbc.execute("alter table employees alter column first_name varchar(4096)");
        String marker = "export-limit-" + UUID.randomUUID();
        try {
            var batch = new ArrayList<Object[]>();
            for (int i = 0; i < 1001; i++) batch.add(new Object[]{"界".repeat(1000), "Limit", marker + i + "@example.com", 60000, "ACTIVE", 0});
            jdbc.batchUpdate("insert into employees(first_name,last_name,email,salary,status,version) values (?,?,?,?,?,?)", batch);
            mvc.perform(get("/admin/employee/export.csv").param("column", "email").param("search", marker)
                    .with(user("admin").roles("ADMIN"))).andExpect(status().isPayloadTooLarge()).andExpect(header().doesNotExist("Content-Disposition"));
            jdbc.update("delete from employees where email = ?", marker + "1000@example.com");
            mvc.perform(get("/admin/employee/export.csv").param("column", "firstName").param("search", marker)
                    .with(user("admin").roles("ADMIN"))).andExpect(status().isPayloadTooLarge()).andExpect(header().doesNotExist("Content-Disposition"));
            jdbc.update("update employees set first_name = ? where email = ?", "x".repeat(2049), marker + "0@example.com");
            mvc.perform(get("/admin/employee/export.csv").param("column", "firstName").param("search", marker + "0@example.com")
                    .with(user("admin").roles("ADMIN"))).andExpect(status().isPayloadTooLarge()).andExpect(header().doesNotExist("Content-Disposition"));
        } finally {
            jdbc.update("delete from employees where email like ?", marker + "%");
        }
    }
}
