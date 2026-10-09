package com.example.demo.entity;

import io.github.yavonalabs.vectis.core.annotation.AdminAction;
import io.github.yavonalabs.vectis.core.annotation.AdminEntity;
import io.github.yavonalabs.vectis.core.annotation.AdminIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "employees")
@AdminEntity(label = "Team Members", singularLabel = "Team member", group = "HR Operations")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    @io.github.yavonalabs.vectis.core.annotation.AdminField(description = "Legal first name", order = 10, showInList = false, group = "Identity")
    private String firstName;

    @NotBlank
    @Column(nullable = false)
    @io.github.yavonalabs.vectis.core.annotation.AdminField(description = "Legal last name", order = 20, showInList = false, group = "Identity")
    private String lastName;

    @Email
    @Column(nullable = false, unique = true)
    @io.github.yavonalabs.vectis.core.annotation.AdminField(description = "Corporate email address", order = 30, group = "Identity")
    private String email;

    @jakarta.validation.constraints.Min(30000)
    @jakarta.validation.constraints.Max(500000)
    @Column(precision = 10, scale = 2)
    @io.github.yavonalabs.vectis.core.annotation.AdminField(description = "Annual base salary in USD", currency = "USD", order = 40, showInList = false, group = "Compensation")
    private BigDecimal salary;

    @Enumerated(EnumType.STRING)
    @io.github.yavonalabs.vectis.core.annotation.AdminField(description = "Current employment status", order = 50, group = "Employment")
    private EmploymentStatus status = EmploymentStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    @io.github.yavonalabs.vectis.core.annotation.AdminField(description = "Assigned organizational unit")
    private Department department;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "employee_skills",
        joinColumns = @JoinColumn(name = "employee_id"),
        inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private Set<Skill> skills = new HashSet<>();

    @Version
    private Long version;

    @AdminIgnore
    private String internalSecurityToken = "SECRET_HASH_492";

    public enum EmploymentStatus {
        ACTIVE, ON_LEAVE, TERMINATED
    }

    public Employee() {}

    public Employee(String firstName, String lastName, String email, BigDecimal salary, Department department) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.salary = salary;
        this.department = department;
    }

    @AdminAction(
        label = "Increase annual salary by 15%",
        executionMode = io.github.yavonalabs.vectis.core.action.ActionExecutionMode.MANAGED_LOCAL,
        color = "indigo",
        confirmMessage = "Increase this team member's annual base salary by 15%? This changes the recorded salary; it does not issue a bonus payment.",
        previewMethod = "previewMeritBonus"
    )
    public void grantMeritBonus() {
        if (this.salary != null) {
            this.salary = this.salary.multiply(new BigDecimal("1.15"));
        }
    }

    @AdminAction(
        label = "Change leave status",
        executionMode = io.github.yavonalabs.vectis.core.action.ActionExecutionMode.MANAGED_LOCAL,
        color = "amber",
        confirmMessage = "Switch between active employment and on leave. Review the proposed status below.",
        previewMethod = "previewLeaveStatus"
    )
    public void toggleLeaveStatus() {
        if (this.status == EmploymentStatus.ACTIVE) {
            this.status = EmploymentStatus.ON_LEAVE;
        } else if (this.status == EmploymentStatus.ON_LEAVE) {
            this.status = EmploymentStatus.ACTIVE;
        }
    }

    public java.util.Map<String, Object> previewMeritBonus() {
        return salary == null ? java.util.Map.of() : java.util.Map.of("salary", salary.multiply(new BigDecimal("1.15")));
    }

    public java.util.Map<String, Object> previewLeaveStatus() {
        if (status == EmploymentStatus.ACTIVE) return java.util.Map.of("status", EmploymentStatus.ON_LEAVE);
        if (status == EmploymentStatus.ON_LEAVE) return java.util.Map.of("status", EmploymentStatus.ACTIVE);
        return java.util.Map.of();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public BigDecimal getSalary() { return salary; }
    public void setSalary(BigDecimal salary) { this.salary = salary; }
    public EmploymentStatus getStatus() { return status; }
    public void setStatus(EmploymentStatus status) { this.status = status; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public Set<Skill> getSkills() { return skills; }
    public void setSkills(Set<Skill> skills) { this.skills = skills; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getInternalSecurityToken() { return internalSecurityToken; }
    public void setInternalSecurityToken(String internalSecurityToken) { this.internalSecurityToken = internalSecurityToken; }

    @Override
    public String toString() {
        return firstName + " " + lastName + " (" + email + ")";
    }
}
