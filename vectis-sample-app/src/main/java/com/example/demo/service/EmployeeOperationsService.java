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
        label = "Increase annual salary by 20%",
        color = "emerald",
        confirmMessage = "Increase the recorded annual base salary by 20%? This does not issue a payment.",
        risk = RiskLevel.MODERATE,
        previewMethod = "previewPromotion"
    )
    public void promoteEmployee(Employee employee, Map<String, String> params) {
        if (employee.getSalary() != null) {
            employee.setSalary(employee.getSalary().multiply(new BigDecimal("1.20")));
        }
    }

    public Map<String, Object> previewPromotion(Employee employee, Map<String, String> params) {
        return employee.getSalary() == null ? Map.of() : Map.of("salary", employee.getSalary().multiply(new BigDecimal("1.20")));
    }

    public Map<String, Object> previewTermination(Employee employee, Map<String, String> params) {
        return Map.of("status", Employee.EmploymentStatus.TERMINATED);
    }

    @AdminAction(
        label = "Terminate Employee",
        color = "rose",
        confirmMessage = "Mark this team member as terminated? The recorded salary is retained. This action does not revoke access in other systems.",
        risk = RiskLevel.CRITICAL,
        previewMethod = "previewTermination"
    )
    public void terminateEmployee(Employee employee, Map<String, String> params) {
        employee.setStatus(Employee.EmploymentStatus.TERMINATED);
    }
}