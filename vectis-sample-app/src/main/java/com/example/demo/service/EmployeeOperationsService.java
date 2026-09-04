package com.example.demo.service;

import com.example.demo.entity.Employee;
import io.github.yavonalabs.vectis.core.annotation.AdminAction;
import io.github.yavonalabs.vectis.core.annotation.RiskLevel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;

@Service
public class EmployeeOperationsService {

    @PreAuthorize("hasRole('ADMIN')")
    @AdminAction(
        label = "Grant 20% Promotion",
        color = "emerald",
        confirmMessage = "Grant a 20% promotion raise through the Spring service layer?",
        risk = RiskLevel.MODERATE
    )
    public void promoteEmployee(Employee employee, Map<String, String> params) {
        if (employee.getSalary() != null) {
            employee.setSalary(employee.getSalary().multiply(new BigDecimal("1.20")));
        }
    }

    @AdminAction(
        label = "Terminate Employee",
        color = "rose",
        confirmMessage = "Terminate this employee and revoke system access immediately?",
        risk = RiskLevel.CRITICAL
    )
    public void terminateEmployee(Employee employee, Map<String, String> params) {
        employee.setStatus(Employee.EmploymentStatus.TERMINATED);
        employee.setSalary(BigDecimal.ZERO);
    }
}