# Operator UI milestone

13 September 2026. Implements the first slice of [the UI/UX plan](UI_UX_AND_PRODUCT_STRATEGY.md), on top of the existing safety fixes.

## Changes

- Record lists lead with a linked business name. IDs, versions and database-type badges are removed from the default list. In the team-member example, first/last names form a single identity, followed by email, status, department and an accessible actions control.
- `@AdminField` now supports `label`, `order`, `showInList` and an explicit ISO currency code. The previous `FieldDescriptor` constructor remains available for existing integrations. `showInList` controls presentation only; it does not restrict access to the field.
- `@AdminEntity(singularLabel = "...")` supplies a singular form heading. Without it, the entity name is the fallback. Existing plural navigation labels continue to work.
- Shared formatting renders empty values, booleans and enum names in plain language. Decimal values are ordinary numbers unless a currency is declared. Currency uses an explicit code, and numeric precision is retained. Number grouping currently uses US conventions; configurable locale and timezone formatting remain future work.
- Forms associate validation errors with individual fields, provide a linked error summary, and retain submitted values and reasons on Jakarta validation failures. HTMX validation returns a form fragment. General conversion errors and all unsaved-edit/concurrency recovery flows still need further work.
- Action dialogs identify the record by name. The action list sorts lower-risk tasks ahead of higher-risk tasks. Confirmation dialogs support keyboard focus wrapping, Escape and return focus; deletion reasons reset on opening.
- Navigation, login, details and relationship labels use simpler language. Shared CSS increases common text/control sizes, keeps the table action column accessible while scrolling, and provides visible keyboard focus, a skip link and reduced-motion behavior.
- Technical metadata is collapsed on the detail page. Unsupported real-time/transactional wording was removed from affected screens.

## Sample behavior corrections

“Grant 15% Bonus” is now “Increase annual salary by 15%.” Both salary actions explicitly describe updating recorded annual salary, not issuing a payment.

Termination changes only employment status and preserves the recorded salary. Its preview and handler agree, and the existing salary constraints remain in force. The confirmation no longer promises to revoke access in other systems. This is a sample-domain decision, not a universal employment workflow.

## Integration example

```java
@AdminEntity(label = "Team Members", singularLabel = "Team member")
class Employee {
    @AdminField(description = "Annual base salary in USD",
                currency = "USD", order = 40, showInList = false)
    private BigDecimal salary;
}
```

Currency configuration is validated during metadata discovery. Hidden-in-list fields remain available in permitted detail/form/preview views. Continue to use explicit exposure and server authorization for access control.

## Remaining roadmap

This milestone does not implement typed filters, saved views, user column preferences, a light theme, paginated relationship lookup, approvals, durable duplicate protection, or a shared mutation transaction. Third-party UI dependencies still use CDNs. It is not a claim of full accessibility conformance or production readiness.

The next priority is typed filters and bounded relationship selection, alongside the engineering roadmap's execution guarantees.

## Verification

- `mvn verify` succeeded across all five modules: 40 tests passed, no failures or errors. Results are in `operator-ui-test-output.log` at the repository root.
- Repackaged after the final breadcrumb/whitespace cleanup with `mvn package -DskipTests`; the updated sample is running at `http://localhost:18080/admin`.
- Browser checks covered sign-in, a 1280 × 720 desktop table with all five columns visible without horizontal scrolling, full record details, readable currency/status values, and an action preview that remained disabled until a reason was provided.
- Verified keyboard Tab wrapping inside the action dialog and closing with Escape.
- Submitted an invalid whitespace-only first name in the disposable sample. The server returned a focused, linked error summary and inline field error while retaining the other values and reason. Correcting it saved successfully, returned to the record, and displayed a success message and activity entry.
- `git diff --check` passed. Narrow-device and full assistive-technology audits remain outstanding; the desktop checks are not an accessibility certification.
