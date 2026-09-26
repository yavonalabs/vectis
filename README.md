# Vectis ⚙️
> **Give your operations team leverage over your Spring Boot application — not your database.**

[![Build](https://github.com/yavonalabs/vectis/actions/workflows/ci.yml/badge.svg)](https://github.com/yavonalabs/vectis)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Vectis** (*Latin for Lever*) is an in-app operations and support console for **Spring Boot 3 & Java 17+**. It provides operations, customer support, and back-office teams with a secure, audited interface to inspect data and trigger domain actions without giving them direct database access or requiring engineers to build custom React/Retool admin apps.

---

## 💡 Why Vectis?

| Dimension | Direct DB / DBeaver | Retool / Low-Code | **Vectis** |
| :--- | :--- | :--- | :--- |
| **Security & Safety** | 🔴 Direct DB writes bypass all Java rules | 🟡 Custom SQL queries can corrupt data | 🟢 **Runs inside Spring context** |
| **Domain Logic** | 🔴 No events, no notifications fired | 🔴 Blind updates | 🟢 **Triggers real `@AdminAction` methods** |
| **Validation** | 🔴 DB-level constraints only | 🟡 Manual frontend rules | 🟢 **Honors `@NotNull`, `@Email`, `@Size`** |
| **Discovery** | 🔴 Everything exposed | 🟡 Manual query building | 🟢 **Strict opt-in via `@AdminEntity`** |
| **Setup** | 🔴 Requires database port exposure | 🔴 Requires external server connection | 🟢 **Single dependency, 0 external servers** |

---

## 🚀 Quickstart

This checkout builds `0.1.0-SNAPSHOT`. Until a published release is independently verified, install the modules locally from the repository root with Java 17 and Maven:

```sh
mvn clean install
java -jar vectis-sample-app/target/vectis-sample-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo
```

Open `http://localhost:8080/login` to try the fictional sample data. Use `admin / admin` for editing or `user / password` for read-only access. Sample data and activity reset on restart. These accounts are for the sample application only.

For the hosted demo configuration, domain decision and release checks, see [the deployment runbook](docs/DEPLOYMENT.md).

### 1. Add the Dependency

```xml
<dependency>
    <groupId>io.github.yavonalabs</groupId>
    <artifactId>vectis-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Mark Entities for Management

```java
@Entity
@Table(name = "customers")
@AdminEntity(label = "Customers", group = "Customer Support")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Email
    private String email;

    @Enumerated(EnumType.STRING)
    private AccountStatus status = AccountStatus.ACTIVE;

    @AdminIgnore // Hidden from UI completely
    private String internalPasswordHash;

    // --- DOMAIN ACTIONS ---
    @AdminAction(
        label = "Suspend Account",
        color = "rose",
        previewMethod = "previewSuspension",
        confirmMessage = "Are you sure you want to suspend this customer? They will lose access immediately."
    )
    public void suspendAccount() {
        this.status = AccountStatus.SUSPENDED;
        // Triggers your domain service, notifications, and events!
    }

    public java.util.Map<String, Object> previewSuspension() {
        return java.util.Map.of("status", AccountStatus.SUSPENDED);
    }
}
```

### 3. Open the Console

Start your Spring Boot app and navigate to:
👉 **`http://localhost:8080/admin`**

Configure the host application's authentication and CSRF protection first. The default
Vectis policy requires `ROLE_ADMIN`; read-only roles can be configured below. See
[access and preview configuration](docs/SAFE_ACTIONS.md) for upgrade requirements,
explicit preview methods, and current limitations.

---

## 🛠️ Configuration (`application.yml`)

```yaml
vectis:
  enabled: true
  path: /admin
  title: ACME Support Console
  environment: Staging
  roles: [ROLE_ADMIN]
  read-only-roles: [ROLE_SUPPORT]
  allowed-entities:
    - com.example.demo.entity.Customer
    - com.example.demo.entity.Order
```

---

## 🏢 Enterprise & Open-Core Model

Vectis is distributed under the **Apache 2.0 License** for its open-source core. 

For security, compliance, and enterprise support teams, **Vectis Enterprise** provides:
* **Visual Audit Trail & Before/After Diff Viewer**
* **SSO / SAML 2.0 / OIDC (Okta, Keycloak, Azure AD)**
* **Granular Field-Level & Action-Level RBAC**
* **Dual-Authorization Approval Workflows**

---

## 📄 License

Copyright © 2026 [YavonaLabs](https://yavonalabs.com). Released under the [Apache 2.0 License](LICENSE).
