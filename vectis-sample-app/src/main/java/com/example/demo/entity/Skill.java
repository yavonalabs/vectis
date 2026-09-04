package com.example.demo.entity;

import io.github.yavonalabs.vectis.core.annotation.AdminEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "skills")
@AdminEntity(label = "Technical Skills", group = "Organization")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String name;

    @ManyToMany(mappedBy = "skills")
    private Set<Employee> employees = new HashSet<>();

    public Skill() {}

    public Skill(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<Employee> getEmployees() { return employees; }
    public void setEmployees(Set<Employee> employees) { this.employees = employees; }

    @Override
    public String toString() {
        return name;
    }
}