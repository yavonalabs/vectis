package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
public class ActionPreviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "USER")
    void readOnlyOperatorCanBrowseButCannotMutateOrReadAudit() throws Exception {
        var result = mockMvc.perform(MockMvcRequestBuilders.get("/admin/employee"))
                .andExpect(status().isOk()).andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .doesNotContain("href=\"/admin/employee/create\"", "data-action-id=\"promoteEmployee\"", "href=\"/admin/audit\"");
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/employee/action/toggleLeaveStatus/3")
                .param("_reason", "Not permitted").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/employee/save")
                .param("__id", "3").param("_reason", "Not permitted").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/audit")).andExpect(status().isForbidden());
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/api/search").param("q", "Alice"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testPromoteEmployeePreview_IsReachableAndReturnsCorrectJsonShape() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/api/employee/action/promoteEmployee/3/preview")
                .with(csrf())
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasChanges").value(true))
                .andExpect(jsonPath("$.riskLevel").value("MODERATE"))
                // Ensure plainTextChanges contains the formatted string
                .andExpect(jsonPath("$.plainTextChanges").isArray())
                .andExpect(jsonPath("$.plainTextChanges[0]").value("Salary changed from 95,000.00 USD to 114,000.00 USD"));
    }
}
