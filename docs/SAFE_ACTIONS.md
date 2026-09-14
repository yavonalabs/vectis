# Configuring access and action previews

The first improvement milestone changes security defaults and preview behavior. Existing applications should review these changes before upgrading.

## Access

```yaml
vectis:
  enabled: true
  path: /admin
  title: Support Console
  environment: Staging
  roles: [ROLE_ADMIN]
  read-only-roles: [ROLE_SUPPORT]
```

`roles` permits console access and mutations; its default is `ROLE_ADMIN`. `read-only-roles` defaults to an empty list. Read-only users can browse entities and search but cannot edit, delete, execute/preview actions, or read the audit trail under the default evaluator. Users in both lists retain their mutation permissions.

The host application supplies authentication and an HTTP security filter chain, including CSRF protection. The sample demonstrates Spring method security using `@EnableMethodSecurity`. Vectis does not replace the host's filter chain. Configure its request matchers to cover the chosen Vectis path.

Without a supported Spring Security integration or a custom `AdminPermissionEvaluator`, the default evaluator denies access. The old `AllowAllPermissionEvaluator` remains available only for explicit integration choices; it is no longer installed automatically.

Provide an `AdminPermissionEvaluator` bean for entity-specific policies. Every action execution and preview checks the evaluator, even if `requiredRole` is empty. A configured role requirement and preauthorization expression must both pass. The default expression evaluator supports basic role/authority checks; it is not a replacement for full Spring method-security expression semantics. Unsupported expressions deny access. Service actions should use real Spring method security as well.

Entity opt-in still applies. These controls do not implement row-level multi-tenant isolation or granular field-level RBAC.

## Explicit previews

Opening a preview no longer invokes the action's execution handler. Annotated actions can name a separate public method on the same class with the same parameter signature and a `Map<String, Object>` return value:

```java
@PreAuthorize("hasRole('ADMIN')")
@AdminAction(label = "Grant promotion", previewMethod = "previewPromotion")
public void promote(Employee employee, Map<String, String> params) {
    employee.setSalary(employee.getSalary().multiply(new BigDecimal("1.20")));
}

public Map<String, Object> previewPromotion(Employee employee, Map<String, String> params) {
    return Map.of("salary", employee.getSalary().multiply(new BigDecimal("1.20")));
}
```

Entity methods with no parameters use a no-parameter preview method. Programmatic `EntityAction` registrations can supply `.preview((entity, params) -> proposedValues)`.

The map describes proposed values for visible scalar fields. IDs, versions, ignored properties, and unknown properties are not included in the displayed changes. Jakarta validation checks the projected state. Use a map implementation that accepts nulls if the proposed value is null. Numbers are displayed without assuming a currency.

Preview methods must be side-effect-free: no emails, HTTP calls, jobs, persistence, or mutation of related objects. An entity copy is supplied, but its associated objects are not deep-copied. This is a trusted developer contract, not a sandbox. The registered protection aspect rejects intercepted `@ExternalApiCall` calls during a preview; it cannot intercept every possible external effect or self-invocation.

Actions without an explicit preview return HTTP 422 from the preview endpoint. The UI explains that preview is unavailable and keeps confirmation disabled. Direct execution requests still use the normal authorization and validation rules; a preview is not an authorization token.

If a preview fails, confirmation stays disabled. If the preview projects invalid data, it returns HTTP 422. A submitted preview version is compared with the current record version, with HTTP 409 on mismatch. This check does not provide durable retry deduplication or eliminate all concurrency races; those are part of the next transaction milestone.

## Reasons and forms

Create, update, and delete requests require a nonblank `_reason` of at most 1,000 characters. Moderate, high, and critical actions require it too. CSRF remains required by the host's configuration. The UI sends the reason and preview version automatically.

Successful HTMX saves return an `HX-Redirect` to the saved record and a success message. Normal form submissions retain regular redirects. Jakarta validation failures retain the form and reason. Pagination supports sizes 1–100 and rejects negative or overflowing offsets.

Configured paths and servlet context paths are used in navigation, search URLs, forms, and preview requests. `vectis.environment` is an explicit label; Vectis does not infer that a deployment is production.

## Remaining work

Audit persistence and mutations still need a shared transaction contract for standard CRUD, with explicit handling of service actions that use independent transactions. Durable idempotency, broader metadata/database compatibility, structured filters, export, and the complete operator-view redesign remain on the roadmap. The sample termination preview intentionally rejects its zero-salary result under the current `@Min(30000)` constraint; the domain rule needs an explicit decision before that sample action is usable.
