# Vectis Studio improvement plan

Prepared 12 September 2026 from the code review and local testing recorded in [VECTIS_REVIEW.md](VECTIS_REVIEW.md).

Implementation update, 13 September: the first set of safety and workflow fixes is implemented and verified. See [the milestone report](docs/PHASE1_STATUS.md) for completed work, test results, compatibility changes, and remaining scope.

UI/UX and product planning update, 13 September: see [the detailed UI/UX and competitive strategy](docs/UI_UX_AND_PRODUCT_STRATEGY.md) for the current interface review, KraftAdmin/SnapAdmin comparison, screen-level requirements, prioritized features, delivery dependencies, and pilot success measures.

Operator UI implementation: [the first UI milestone](docs/OPERATOR_UI_MILESTONE.md) records the simplified tables, presentation metadata, form feedback, and corrected sample action behavior now implemented.

14 September update: [typed filters and relationship lookup](docs/FILTERS_AND_RELATIONSHIPS_MILESTONE.md) adds combined scalar conditions, preserved query state, and bounded relationship selectors.

## 1. Product outcome and scope

**Make Vectis the place where a support operator can find a record, understand its state, perform an approved business task, and verify the result without writing SQL.**

The first useful release should solve a small set of everyday tasks reliably. Developer configuration, permissions, and business rules remain the developer's responsibility; operators should not need to understand the database schema to use the resulting console.

### Target users

| User | Main need | First-release experience |
|---|---|---|
| Support operator | Find a record and complete a permitted task | Plain-language search, record summary, named actions, clear results |
| Support supervisor | Investigate activity and handle more sensitive tasks | Additional permitted actions, activity history, reasons and actors |
| Application developer | Expose selected data and business operations quickly | Starter, explicit metadata, service integration, configuration diagnostics |
| Read-only reviewer | Inspect data without changing it | Search and details; no mutation controls or mutation access |

These are example role templates, not hardcoded authority names. Host applications must be able to map their existing identities and policies onto Vectis.

### First-release workflow

Use the existing sample to prove this complete journey:

1. Search for a team member by name, email, or ID.
2. Open a summary showing their name, status, department, and permitted details.
3. Choose a named operation such as “Place on leave.”
4. Read the expected change or an explicit explanation that a preview is unavailable.
5. Enter a reason when required and confirm.
6. See the actual result and a link to the recorded activity.
7. Return to the original filtered list without losing context.

Also support safe correction of explicitly editable fields, creation where configured, and authorized export. Generic editing must not imply that all application business-service rules run automatically.

### Boundaries

- Keep the existing Spring Boot/JPA architecture and server-rendered UI unless testing demonstrates a reason to change them.
- Retain entity opt-in. Adding a dependency must not expose every entity or grant mutation permissions.
- Make the default operator interface usable with zero SQL knowledge.
- Do not add a raw SQL editor, natural-language SQL execution, broad bulk writes, or a separate frontend rewrite in this roadmap.
- Defer approval workflows, built-in SSO-provider management, and multi-application administration until the core workflows are reliable. Continue integrating with the host application's authentication.
- Treat multi-tenant support as a separate release decision. Do not market tenant isolation until every access path has proven tenant scoping.

## 2. Delivery sequence and estimated effort

These are planning estimates, not delivery commitments. Assume one developer familiar with Spring, access to a reviewer for security-sensitive changes, and short sessions with representative operators. Expect roughly **8–12 calendar weeks** for a limited pilot, with re-estimation after the first phase. Existing application-specific integration work may extend this.

| Phase | Approximate effort | Deliverable | Exit requirement |
|---|---|---|---|
| 1. Restore safety and basic workflows | 8–12 developer days | Internally testable console | Confirmed high-priority defects fixed and tested |
| 2. Unify execution, preview, and audit | 7–10 days | Trustworthy action lifecycle | Permission, concurrency, rollback, and retry tests pass |
| 3. Design the operator experience | 6–9 days | Usable end-to-end workflows | Representative users complete core tasks |
| 4. Improve finding and moving through data | 5–8 days | Reliable search, filters, relationships, export | Correct results and bounded queries |
| 5. Validate starter integration and deployment | 5–8 days | Documented pilot package | Fresh-app and database compatibility checks pass |
| 6. Run the pilot and refine | 5–8 days plus observation | Evidence for a release decision | Pilot acceptance targets met; serious issues resolved |

Finish each phase's exit requirements before treating its features as ready. UI design can be sketched during earlier phases, but mutation flows depend on the execution and permission contracts.

## 3. Phase 1 — Fix the demonstrated defects

### P0-01: Apply authorization at every entry point

Start in `AdminController`, `ActionPreviewController`, `SpringSecurityPermissionEvaluator`, and the action registry.

- Check permission for every action execution and preview, regardless of whether an action uses `requiredRole`, a Spring method annotation, or a custom evaluator.
- Define explicit policies for console access, entity listing/detail, record access, create, update, delete, action execution, audit viewing, and export.
- Reject anonymous tokens in the evaluator. Test both HTTP security and direct service authorization.
- Apply the same policy to search results, relationship labels/options, dashboard statistics, and activity summaries. Hidden links must not substitute for server checks.
- Support Spring method-security proxies correctly. Decide and document the supported annotation semantics; do not present a small custom expression evaluator as full Spring authorization compatibility.
- Default to denial when a configured policy cannot be evaluated. Mutation access should require deliberate configuration; any development-only permissive mode must be explicit.

**Acceptance:** a denied user cannot obtain protected data or change it through direct requests, alternate endpoints, preview, or relationships. The same action has consistent access rules in its UI and server handler. The regular-user promotion and anonymous-token regressions pass.

### P0-02: Prevent hidden-field disclosure

- Create a shared projection of permitted fields for list, detail, preview, search, export, and audit presentation.
- Exclude ignored properties from diffs, including nested changes. Never fall back to returning a hidden property's raw value.
- Avoid arbitrary entity `toString()` output for related records when it might include protected information; allow developers to configure a safe display label.
- Define hidden, masked, readable, and editable as distinct concepts. For the first release, implement only the combinations that are tested and documented.

**Acceptance:** seeded secret values never appear in forbidden HTML, JSON, search labels, CSV, or activity output. Both field-access and supported property-access entities are covered.

### P0-03: Contain preview side effects immediately

- Register the existing protection aspect and test its proxy behavior as an interim repair.
- Disable arbitrary handler-based previews by default until the explicit preview contract in Phase 2 is available. Allow execution only through the normal permission and confirmation flow when a policy permits execution without preview.
- Show “Preview unavailable for this action” where appropriate. For actions configured to require preview, fail closed when preview fails or is unavailable.
- Document that a database rollback cannot undo an email, webhook, asynchronous job, or independent transaction.

**Acceptance:** opening a confirmation dialog does not run an action's real handler. The simulated external-call regression passes under the selected preview strategy. No UI claims general external-effect protection based only on an aspect.

### P0-04: Repair action and save interactions

- Fix the Alpine expression syntax error and move substantial modal logic into a locally served JavaScript file.
- Define loading, ready, validation-error, failed, submitting, and success states.
- For successful HTMX saves, send a navigation response or a matching success fragment. For normal forms, retain ordinary redirects.
- Preserve input on validation errors; show a summary and field-level messages.
- Reset modal state between records and actions. Do not reuse a previous reason or preview accidentally.
- Prevent repeated submission while a request is pending; add durable duplicate-execution protection in Phase 2.

**Acceptance:** create, edit, preview, cancel, and execute work in a browser with visible feedback and no uncaught JavaScript errors. The newly created record is shown or linked immediately after saving.

### P0-05: Repair configuration and request validation

- Register global search and required exception handlers explicitly through auto-configuration.
- Resolve configured base paths consistently in mappings, forms, links, redirects, JavaScript, and API endpoints; test servlet context paths too.
- Validate reasons on the server according to action policy. Reject whitespace-only reasons.
- Validate pagination and sort input, including upper limits and arithmetic overflow. Start with a configurable maximum page size of 100.
- Return consistent 400/403/404/409 responses, appropriate to the request, without exposing internal exception details.
- Remove or clearly disable the export placeholder until Phase 4.
- Replace static “Production” and “Safety checks ON” indicators with accurate configuration/status information.

**Acceptance:** the current 21 review tests pass, or are deliberately migrated to test an explicitly changed contract with the original defect still covered. No disabling failing tests to obtain a green build. Test `/admin`, `/ops`, and an application context path.

## 4. Phase 2 — Make business operations trustworthy

### A. Centralize the action lifecycle

Introduce a shared application service for validation and action execution. Controllers should translate requests and responses rather than implement separate business-operation pipelines.

Execution order:

1. Resolve the actor, entity, record, and action.
2. Check access and validate action inputs and reason.
3. Reload current state and check its version and action eligibility.
4. Invoke the configured business service or allowed CRUD operation.
5. Validate the resulting state.
6. Persist the change and required audit record under the documented transaction contract.
7. Return the actual outcome, activity identifier, and navigation target.

Where domain actions already perform their own persistence, avoid blindly merging a stale entity afterward. Define whether an action mutates a managed entity or delegates to an application service that returns an updated result; support explicit adapters for those cases.

### B. Introduce an explicit preview contract

Define separate `preview` and `execute` handlers. Names are illustrative, not an API commitment.

The preview returns permitted field changes, affected-record information, validation messages, action risk, and the record version on which it was based. Its implementation must be side-effect-free by contract. Tests verify that contract for supplied actions; a read-only transaction alone does not prove it.

- Preview and execution share input validation and business-rule evaluation where practical.
- Execution reloads and revalidates state; a preview is not an authorization token or a guarantee that a later action will succeed.
- If the record changed after preview, return a conflict and ask the operator to review the current state.
- Distinguish “no changes expected” from “preview unavailable” and “preview failed.”
- Correct the sample termination operation: decide the intended salary rule and make preview and execution agree on it.

**Acceptance:** tests cover no-op, invalid input, invalid final state, missing preview, failed preview, version conflict, and simulated external effects. A preview never authorizes an otherwise forbidden execution.

### C. Make audit behavior explicit

- Record actor, action, entity identity, timestamp, reason, permitted before/after values, result, and correlation identifier.
- For standard local JPA mutations, persist the mutation and required audit in the same transaction. If required audit persistence fails, the mutation must roll back.
- Define separate failure/attempt logging; do not record a failed operation as a successful change.
- Detect or document service actions using independent transactions; do not claim atomicity across them.
- For integrations with external effects, use a documented durable handoff/outbox pattern where appropriate and display pending or failed delivery honestly.
- Include meaningful relationship changes and avoid storing secret snapshots. Restrict activity access separately from entity access.
- Describe the trail as an application audit record unless stronger tamper-resistance is implemented. Database administrators may still be able to alter stored data.

**Acceptance:** audit-storage failure, action exception, validation failure, transaction rollback, and relationship changes have verified outcomes. A successful standard mutation has exactly one successful audit record.

### D. Handle retries and concurrent edits

- Require record versions for updates of versioned entities and return a useful conflict screen.
- Add a persisted idempotency key for operations vulnerable to double submission or network retries. Scope it to actor, action, target, and request content.
- Reject reuse with different inputs. Define retention and a retryable outcome policy.
- Do not advertise universal “undo.” Implement explicit compensating business actions only where the application supports them.

**Acceptance:** double-clicking or retrying the same accepted request does not apply a salary change twice. Two operators editing the same version cannot silently overwrite one another.

## 5. Phase 3 — Make the interface understandable

### Operator view

- Home: permitted tasks, search, relevant recent activity, and saved views.
- List: familiar labels and useful default columns; hide technical types, internal version fields, and schema names.
- Detail: identity and status first, important business information second, related records and activity afterward.
- Actions: specific verbs such as “Place on leave” rather than “Toggle status.” Include the target's name and current state in confirmation.
- Forms: logical field order, concise examples, required indicators, inline validation, and a clear save result.
- Activity: show “Who changed what, when, and why” in plain language, with a details expansion for technical information.

Keep a developer view for technical metadata and diagnostics. Switching views must never grant additional permissions.

### Developer-controlled presentation

Extend metadata only where the operator workflows need it: singular/plural labels, section grouping, column order, safe display name, editable fields, enum labels, and number/date formatting. Do not format every decimal as USD. Support currency and timezone explicitly when configured.

### Interaction and accessibility

- Use named buttons and associated labels; provide keyboard access to row actions.
- Give dialogs focus management, Escape behavior, and focus restoration.
- Announce errors and success to assistive technology.
- Test contrast, zoom, narrow layouts, and horizontal-table behavior.
- Preserve search/filter/page context when opening and returning from a record.
- Warn about unsaved edits and give useful empty and unavailable states.

**Acceptance:** representative operators complete finding a record, making an allowed correction, running an action, and finding its audit entry without SQL help. Collect observed mistakes rather than relying only on satisfaction ratings.

## 6. Phase 4 — Make data discovery useful

### Search and structured filters

- Search supported IDs, names, email, and developer-configured fields. Apply permissions before limiting and returning results.
- Add a small filter builder: Field → Operator → Value.
- Support exact enum/boolean filters, text matching, numeric ranges, dates, and null values where appropriate.
- Keep a text search distinct from exact status filters. Do not use a broad substring search to implement “Status is Active.”
- Validate filter types, operators, sort fields, and complexity on the server. Continue using parameterized criteria queries.
- Use stable pagination ordering with an ID tie-breaker. Preserve filters and sort in URLs.
- Start saved views as filters/sorts/columns, not snapshots of data; reevaluate permissions whenever opened.

### Relationships

- Replace full-table association dropdowns with permission-aware server-side search.
- Paginate large collections on detail screens.
- Use the target entity's identifier metadata and safe display label.
- Handle inaccessible, removed, and unavailable related records without exposing their contents.

### Export

- Define the scope clearly: selected records or all records matching the current filters.
- Show scope and estimated count before export. Enforce permission and configured limits on the server.
- Export only permitted fields; escape CSV correctly and neutralize spreadsheet formula injection.
- Stream bounded output and audit the export when configured. Avoid background jobs until volume requires them.

**Acceptance:** filters return exactly the expected records; export matches its stated scope; no hidden fields leak; large relationship sets do not generate unbounded page queries.

## 7. Phase 5 — Make installation dependable

### Fresh-application integration

Build a minimal consuming application outside the sample's scanning setup. Verify that adding Vectis neither replaces the host application's entity scanning nor depends on undocumented component scans.

- Wire controllers, advice, services, audit entities, and optional security integration deliberately.
- Test enabled/disabled mode, custom bean overrides, missing optional dependencies, and custom paths.
- Document schema creation/migration requirements. Do not use `ddl-auto=create-drop` as a production deployment recipe.
- Publish an explicit identifier and mapping support matrix. Support tested scalar IDs first; gate composite IDs and property-access mappings on end-to-end tests.
- Bundle pinned frontend assets and remove runtime CDN requirements for normal operation.

### Developer onboarding

Provide a reproducible quickstart containing one entity, one configured role, one business action, and one preview. Include expected screens and troubleshooting for no entities, no actions, denied access, and schema failures.

Add startup diagnostics that identify duplicate action IDs, invalid handlers, unsupported identifier mappings, and missing preview/configuration requirements. Avoid logging secret values.

**Acceptance:** a developer unfamiliar with Vectis can complete the documented sample setup in a target of 30 minutes on a supported environment. This is a target to measure, not a current claim.

### Compatibility and scale

Use automated integration tests with real PostgreSQL and MySQL instances for the database versions chosen for support, alongside fast H2 tests. Confirm ordering, enum filtering, constraints, transactions, and pagination across them.

Create a synthetic dataset of at least 100,000 primary records with substantial relationships. Record hardware, indexes, concurrency, query counts, and response distributions. Start with a proposed p95 target of one second for common indexed list/search/detail requests at ten concurrent operators; adjust only with recorded measurements and a clear use case. Bound expensive counts and searches.

## 8. Test and release strategy

| Layer | Required coverage |
|---|---|
| Unit | Policy decisions, projections/redaction, metadata, filter validation, ID encoding, formatting |
| Spring integration | Real security proxies, transactions, audit failures, rollback, versions, duplicate requests |
| HTTP | Authentication, CSRF, direct forbidden requests, malformed inputs, configuration paths |
| Browser | Find → inspect → preview → execute → verify; create/edit validation; navigation; useful errors |
| Database compatibility | Supported database and mapping combinations |
| Adversarial data | Hidden fields, malicious HTML/text, CSV formulas, invalid associations, large inputs |
| Performance | Large tables, bounded relationships, slow queries, realistic operator concurrency |
| Accessibility | Keyboard-only tasks, dialog focus, labels, announcements, contrast and zoom |

Keep ordinary pull-request checks fast: unit/integration tests and a small browser smoke suite. Run broader database and performance coverage at release checkpoints. Save browser screenshots and logs on failures.

A release is blocked by unauthorized access, secret disclosure, unintended preview effects, silent duplicate execution, data corruption, or an unusable core task. Do not use the percentage of passing tests as the sole readiness measure.

## 9. Phase 6 — Pilot and measure usefulness

Use a staging environment with synthetic or appropriately prepared data first. Recruit three to five representative operators and at least one integrating developer. Limit the first pilot to a few entities and actions.

Give users these tasks without coaching through the interface:

1. Find a specific record using an email or identifier.
2. List records matching an exact status and a second condition.
3. Correct an allowed field and explain whether saving succeeded.
4. Perform a permitted action and explain its result.
5. Find who made a change and why.
6. Encounter a forbidden action or validation failure and recover.

Suggested pilot targets:

| Measure | Target for pilot review |
|---|---|
| Independent completion | At least 90% across the scripted tasks |
| Record lookup | Median under 30 seconds in the test dataset |
| Simple permitted action | Median under 60 seconds once the record is open |
| Outcome clarity | Users correctly identify success, failure, and no-change outcomes |
| Security failures | Zero known authorization or hidden-data failures |
| Duplicate execution | Zero in retry and double-submit tests |
| Required audit coverage | Every successful supported mutation has its expected audit |
| Developer setup | Complete the supported quickstart without undocumented changes |

These are proposed acceptance goals. The small pilot provides usability evidence, not statistical proof. Compare engineer-assisted support work before and during the pilot to see whether Vectis actually reduces interruptions.

Start any production introduction with an explicit entity/action allowlist, read-only access where possible, a configuration kill switch for mutations, and a tested rollback procedure for the software release. Enable writes only after the applicable release requirements are met.

## 10. Ownership and immediate next steps

Assign an implementation owner and reviewer to each P0 item. Security and transaction changes need independent review; that can be a second engineer for a short review rather than a full-time additional team member. A product owner chooses the permitted workflows and an operator representative validates their language and behavior.

**First working sequence:**

1. Convert the review findings into tracked P0 tickets, linking each failing test and reproduction.
2. Agree on the initial entity/action/role matrix and the explicit preview direction.
3. Fix unconditional authorization and anonymous-token handling.
4. Fix hidden-field projections and contain unsafe previews.
5. Repair the action modal and successful-save navigation.
6. Fix configured paths, component registration, and input validation.
7. Run the baseline, regression, and new browser tests; review the changes before beginning broader feature work.

The first milestone is a console that developers can trust and operators can understand. Subsequent features should demonstrate that they shorten a real support task or remove a recurring source of mistakes.
