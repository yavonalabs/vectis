# Vectis: Product Requirements & Strategy Document (v2.0)
*The Native Operations & Support Console for Spring Boot 3*
*An Open-Core Product by YavonaLabs (https://yavonalabs.com)*

---

## 1. Executive Summary & Core Positioning

### The Category Shift:
* **Old Framing:** "Auto-generate CRUD database admin dashboards." *(Commoditized & distrusted by senior engineers)*
* **Pivoted Category:** **"The In-App Operations & Support Console for Spring Boot."**

### Tagline:
> **"Give your support team access to your application — not your database."**
> *Vectis is the opt-in operational interface that lets non-engineers safely inspect records and trigger real business operations without manual SQL scripts or un-audited low-code tools.*

---

## 2. The Architectural Moat: "Respects Your Domain"

Unlike external low-code tools (Retool, Appsmith) or raw DB tools (DBeaver) that execute direct SQL writes:
1. **In-Context Execution:** Vectis runs inside your Spring context.
2. **Domain Operations over CRUD:** Triggering an action executes your real Spring domain services and entity methods (validating constraints, firing events, and sending notifications) rather than executing blind `UPDATE` queries.
3. **Native Spring Security & Validation:** Honors `@PreAuthorize`, Jakarta Validation (`@NotNull`, `@Email`), transaction boundaries, and JPA entity lifecycle callbacks.

```
Direct DB Client (Dangerous):
Support Rep ──► Engineer ──► DBeaver (Raw SQL) ──► Database (Bypasses all Java logic)

Vectis (Safe & Audited):
Support Rep ──► Vectis UI ──► Spring Context (Validation & Domain Logic) ──► Database
```

---

## 3. Core Product Pillars & Requirements (v0.2 MVP)

### Pillar 1: Strict Opt-In Discovery (No "Magic" Anxiety)
* **Default Security Stance:** Zero entities are exposed automatically.
* **Opt-In Mechanisms:**
  1. Class-level annotation: `@AdminEntity(label = "Customers", group = "User Ops")`
  2. YAML Configuration allowlist for teams who avoid annotations:
     ```yaml
     vectis:
       allowed-entities:
         - com.example.demo.entity.Customer
         - com.example.demo.entity.Order
     ```
* **Field-Level Redaction:** `@AdminIgnore` completely hides sensitive fields (passwords, tokens, SSNs) from UI tables, forms, and API payloads.

---

## 4. Product Tiering & Open-Core Monetization

Following the **Backpack-style** commercial strategy:

| Tier | Target User | Features Included |
| :--- | :--- | :--- |
| **Community Edition**<br>*(Free / Apache 2.0)* | Solo Devs & Small Teams | • Opt-in `@AdminEntity` discovery<br>• Search, multi-column sorting, filters, pagination<br>• Relationship navigation<br>• Jakarta Validation enforcement<br>• Custom `@AdminAction` buttons with confirm modals<br>• Standard Spring Security integration |
| **Enterprise Pack**<br>*(Paid License via YavonaLabs)* | Security & Compliance Teams | • Visual Audit Trail & Before/After Diff Viewer<br>• SSO / SAML 2.0 / Keycloak Integration<br>• Granular Field-Level & Action-Level RBAC<br>• Approval Workflows (Dual-authorization for sensitive actions) |