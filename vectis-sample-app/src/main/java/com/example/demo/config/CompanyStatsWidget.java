package com.example.demo.config;

import com.example.demo.entity.Department;
import com.example.demo.entity.Employee;
import io.github.yavonalabs.vectis.core.widget.StatCard;
import io.github.yavonalabs.vectis.core.widget.StatCardProvider;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CompanyStatsWidget implements StatCardProvider {

    private final EntityManager entityManager;

    public CompanyStatsWidget(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatCard> getStatCards() {
        Long totalEmployees = entityManager.createQuery("SELECT COUNT(e) FROM Employee e", Long.class).getSingleResult();
        Long totalDepartments = entityManager.createQuery("SELECT COUNT(d) FROM Department d", Long.class).getSingleResult();
        BigDecimal totalPayroll = entityManager.createQuery("SELECT COALESCE(SUM(e.salary), 0) FROM Employee e", BigDecimal.class).getSingleResult();

        return List.of(
            StatCard.make("Total Headcount", String.valueOf(totalEmployees))
                .description("Active team members")
                .descriptionColor("emerald")
                .icon("users"),
            StatCard.make("Total Payroll", "$" + String.format("%,.2f", totalPayroll))
                .description("Monthly compensation budget")
                .descriptionColor("indigo")
                .icon("currency"),
            StatCard.make("Departments", String.valueOf(totalDepartments))
                .description("Organizational divisions")
                .descriptionColor("slate")
                .icon("chart"),
            StatCard.make("System Health", "100%")
                .description("All services operational")
                .descriptionColor("emerald")
                .icon("check")
        );
    }
}