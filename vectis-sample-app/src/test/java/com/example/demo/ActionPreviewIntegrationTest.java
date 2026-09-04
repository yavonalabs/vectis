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
                .andExpect(jsonPath("$.plainTextChanges[0]").value("salary changed from $95,000.00 to $114,000.00"));
    }
}
