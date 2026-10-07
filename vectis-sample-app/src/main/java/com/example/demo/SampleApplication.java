package com.example.demo;

import com.example.demo.entity.Department;
import com.example.demo.entity.Employee;
import com.example.demo.entity.Skill;
import io.github.yavonalabs.vectis.core.audit.VectisAuditLog;
import jakarta.persistence.EntityManager;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

@SpringBootApplication
@EntityScan(basePackages = {"com.example.demo.entity", "io.github.yavonalabs.vectis.core.audit", "io.github.yavonalabs.vectis.core.mutation", "io.github.yavonalabs.vectis.core.view"})
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    @Bean
    public CommandLineRunner dataSeeder(EntityManager em, TransactionTemplate txTemplate) {
        return args -> txTemplate.executeWithoutResult(status -> {
            Department engineering = new Department("Engineering", "BLD-A");
            Department product = new Department("Product & Design", "BLD-B");
            Department operations = new Department("Customer Operations", "BLD-C");

            em.persist(engineering);
            em.persist(product);
            em.persist(operations);

            Skill java = new Skill("Java 21");
            Skill spring = new Skill("Spring Boot 3");
            Skill k8s = new Skill("Kubernetes");
            Skill ux = new Skill("Product Design");

            em.persist(java);
            em.persist(spring);
            em.persist(k8s);
            em.persist(ux);

            Employee e1 = new Employee("Alice", "Vance", "alice.vance@yavonalabs.com", new BigDecimal("145000.00"), engineering);
            e1.getSkills().add(java);
            e1.getSkills().add(spring);

            Employee e2 = new Employee("Bob", "Miller", "bob.miller@yavonalabs.com", new BigDecimal("120000.00"), product);
            e2.getSkills().add(ux);

            Employee e3 = new Employee("Carlos", "Santana", "carlos.santana@yavonalabs.com", new BigDecimal("95000.00"), operations);
            e3.setStatus(Employee.EmploymentStatus.ON_LEAVE);

            Employee e4 = new Employee("Diana", "Prince", "diana.prince@yavonalabs.com", new BigDecimal("160000.00"), engineering);
            e4.getSkills().add(java);
            e4.getSkills().add(k8s);

            em.persist(e1);
            em.persist(e2);
            em.persist(e3);
            em.persist(e4);
        });
    }
}
