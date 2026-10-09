# Candidate integration contract

Updated 9 October 2026. This describes the implementation, not production approval.

## Managed local actions

Opt in explicitly with `@AdminAction(executionMode = ActionExecutionMode.MANAGED_LOCAL)` or the equivalent `EntityAction.executionMode(...)` builder. Existing registrations default to `HOST_MANAGED`; their handler completion does not establish downstream delivery or exactly-once effects.

Managed mode requires a JPA `@Version` field and the host's default transaction manager to own the record, Vectis audit, proposals and receipts in the same database. The handler must perform only local effects in that transaction. It must not start independent transactions, send messages, call payment/email services, or change another database. This is a developer contract, not a sandbox. Business rules involving related records require host-owned eligibility checks and appropriate locking; the root version alone does not protect those records.

Managed execution reauthorizes, reserves the actor-scoped operation key, checks the reviewed version, consumes the required proposal, executes the handler, validates/persists the entity, writes the success audit and completes the receipt. Commit failure prevents a committed result. A no-field-change action still increments the root version. Calling a managed action inside an existing host transaction is rejected instead of returning premature success or committing outside that transaction.

`requiresConfirmation=true` (the default) requires a server-issued review proposal in managed mode. Preview tokens expire after ten minutes and bind the account, entity, action, record, root version and normalized business input. Tokens are single use. The exact completed request may replay its original receipt even after its proposal expires; a different key/input cannot execute a consumed proposal again.

Transport fields `_csrf`, `_list`, `_operation`, `_proposal`, `_version` and `_reason` are excluded from managed handler business input. The reason is separately validated and audited, so a handler cannot reinterpret the operator's justification as an unreviewed business parameter. Legacy host handlers receive an immutable copy of their original input. Handlers that previously mutated the parameter map must be updated.

The default sample opts its leave-status and 15% salary actions into managed mode. The other host-service examples retain the legacy host-owned contract; do not infer managed guarantees for them.

## Replay and recovery

The server-side `MutationExecutor` adapter accepts an immutable `MutationRequest` and derives the actor through the same authenticated provider as the HTML services. It reports structured succeeded/invalid/rejected/conflict/unknown outcomes. `FAILED` is reserved; an exception alone is not evidence of rollback. Calls inside an existing host transaction are rejected because this synchronous adapter cannot report its eventual commit. Save requests must agree on `recordId` and the submitted `__id` (both absent for creation). Managed action results distinguish exact replay; CRUD's nullable `replayed` value deliberately means that its legacy return contract does not distinguish a first commit from replay. Host-handler exceptions, including validation-shaped exceptions after entering the handler, report an unknown outcome.

Use a new UUID `_operation` for a new request and preserve the same key, proposal and input when recovering that request. Same-key/different-input attempts conflict. Reauthorization precedes replay. Completed receipts are retained indefinitely: do not delete them on a timer, because deleting a receipt would permit an old request to execute again. Expired proposal rows may be deleted because missing proposals are rejected.

`GET {vectis.path}/api/operations/{operationId}` reports an account's authorized managed result, with `Cache-Control: no-store`. Another account cannot retrieve it. Revoking the applicable operation/entity permission also denies recovery. A missing result is not proof that a host/external action had no effect. Old receipts without the new metadata still protect exact retries but cannot be inspected through this endpoint.

Forms expose their reference under “Recover an uncertain save”; managed action reviews expose a recovery reference. The result opens in a new tab at `{vectis.path}/operations/{operationId}`, preserving entered form values. The page uses the same authorization boundary as the JSON endpoint and distinguishes a committed local result from an unconfirmed outcome. Opening it never executes or retries a mutation. Manual `AdminController` construction now also requires `OperationResultController`; starter wiring supplies it.

Success audits include the operation ID for new managed requests. Record activity projects old JSON snapshots through current exposure metadata; removed/hidden fields are not rendered. Failed/unknown host actions do not acquire a fabricated success receipt. A durable general-purpose failure/unknown-outcome journal is not implemented yet.

## Export

Export is denied by default through the new `AdminPermissionEvaluator.canExportEntity` method. The fictional sample explicitly grants it to its write-capable accounts. Existing custom permission implementations remain denied until they opt in. Console and entity access are also required on every request, including direct service calls.

The list's export panel uses the applied search, typed AND filters and sorting, independently of edits not yet applied to the filter form. Operators explicitly select columns. Only currently exposed supported scalar fields are eligible; relationships, hidden fields and versions are rejected. CSV values are raw scalar values, not localized display formatting.

Limits: 1,000 matching rows, 20 selected columns, 2,048 characters per cell and 2 MiB of UTF-8 output. An extra row detects an oversized result, and database-side string projection bounds cell materialization. Over-limit requests fail before an attachment is returned; no silently truncated CSV is presented as complete. The database query has a ten-second timeout hint; host database/driver behavior determines enforcement. Client cancellation does not guarantee immediate database cancellation, but work remains bounded.

CSV quotes/line breaks are escaped, and potentially executable spreadsheet prefixes are neutralized with an apostrophe. This changes those exported values deliberately. Export responses are non-cacheable. This is a synchronous small export, not a bulk data pipeline.

## Upgrade and installation

Apply the host-managed PostgreSQL migrations before deploying against an existing database:

1. Existing installations need the receipt and personal-view tables if not already present: `sql/mutation-receipts-postgresql.sql` and `sql/saved-views-postgresql.sql`.
2. Apply `sql/action-proposals-postgresql.sql`.
3. Apply `sql/operation-recovery-postgresql.sql` for the new nullable receipt/audit columns. Do not reapply migrations already tracked by your migration system.

The disposable sample's `create-drop` configuration is never a production migration strategy. The independent consumer in `verification/consumer` is outside the Maven reactor and resolves installed artifacts. Its restart script checks custom context/admin paths, CSRF, local assets, managed execution, receipt recovery, unchanged state on retry and one success audit across two JVMs. Local evidence uses persistent H2; the separate CI job applies SQL migrations and runs the same verification against PostgreSQL 16 with Hibernate `validate`.

## Bundled assets and CSP

Templates load local, pinned htmx, Alpine, Alpine Anchor, compiled Tailwind utilities and Latin Inter/JetBrains Mono fonts. Other scripts/font servers are not required at runtime. Licenses ship with the assets. Run `npm ci --ignore-scripts` and `npm run build:assets` after template/class changes, and commit the generated assets. CI rebuilds and compares them. Maven consumers do not need Node.

The current Alpine/inline-event template architecture still requires a CSP compatible with inline handlers and Alpine expression evaluation. Bundling assets does **not** establish strict nonce-only/no-`unsafe-eval` CSP support. Do not weaken an existing host CSP silently; test its policy against Vectis or keep this integration disabled until compatible. The October dependency audit also reports build-time Tailwind dependency advisories; those remain a release review item, not a clean-audit claim.

## Build identity

Pass `-Dvectis.build.revision=<full source SHA>` during Maven packaging. The Docker build accepts `RENDER_GIT_COMMIT`; verify the actual build argument supplied by hosting rather than assuming it exists. Authorized `GET {vectis.path}/api/build` returns the packaged revision/version. Unstamped artifacts report `unknown`; a runtime environment variable does not retroactively prove their source. Compare this endpoint with the intended remote commit and record artifact checksums before approving a deployment.
