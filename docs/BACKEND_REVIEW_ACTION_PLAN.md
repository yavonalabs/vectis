# Backend review: validated findings and bounded delivery plan

Date: 7 October 2026. Baseline: `4ba4d57`. Status: implementation scope, not a completion claim.

Purpose: finish a trustworthy developer-preview mutation contract without turning Vectis into a second business layer. Vectis is an authorized operations surface over the host application's business services. The [freeze boundary](CODE_FREEZE_BOUNDARY.md) controls this cycle; the [UI plan](UI_REVIEW_ACTION_PLAN.md) defines the matching operator experience.

## What the review establishes

| Finding | Validated position |
|---|---|
| Managed CRUD is safer than custom actions | Confirmed. Managed CRUD couples record changes, success audit and replay receipts in one default JPA transaction. Custom actions use a separate, legacy execution path. |
| Execution lacks all permission/version checks | Incorrect as a blanket claim. Execution already reauthorizes and checks versions for versioned entities. Binding to a particular reviewed proposal and complete action concurrency remain missing. |
| Preview is a sandbox | Incorrect. Explicit preview handlers replace real-handler execution, but developer code remains trusted. Proxy-based external-call interception is not universal protection. |
| Every field requires an exposure annotation | Incorrect. Entity exposure is opt-in; ordinary mapped fields can be included without `@AdminField`. `@AdminIgnore` excludes fields; presentation flags are not access controls. |
| PostgreSQL testing is absent | Outdated. A PostgreSQL 16 CI job exists. Its latest run, migration application, restart behavior and broader compatibility require separate evidence. |
| Saved views and dirty-form protection are unimplemented | Outdated. Both exist; verification and edge-case closure remain required. |
| Scores establish security or commercial viability | Unsupported. Scores are opinions; tests do not establish demand, revenue or production readiness. |

Latest complete local baseline: 98 Java tests and 17 JavaScript tests passed. During review validation, 58 selected Java regressions and all 17 JavaScript tests passed again. These counts describe runs, not coverage guarantees. No latest PostgreSQL/deployment result is inferred from local H2 success.

## Execution guarantees to implement

One request/result contract may have several executors. Do not force every operation into one transaction or one large service class.

| Operation category | Required boundary for this cycle |
|---|---|
| Managed CRUD | Default host JPA transaction owns record, success audit and receipt; authorization is rechecked for replay. Preserve existing compatibility and failure guarantees. |
| Explicit managed local action | Developer explicitly opts into local-only effects. Mutation, version enforcement, success audit and replay receipt share the selected JPA transaction. This declaration is a host obligation, not a Java sandbox. |
| Host-service / external action | Host owns downstream effects and their idempotency. Expose the execution mode and result honestly. Do not silently upgrade legacy actions to managed guarantees. Unknown outcomes must not be automatically retried. |

Building payment, email, queue or universal outbox integrations is outside this cycle. A host-owned action without a verified recovery/deduplication contract remains excluded from sensitive production-write claims. An `idempotent=true` flag cannot supply that contract.

## Required work packages

All packages below are in the freeze scope. “Remaining” includes finishing and verifying partial implementations, not rewriting working code.

### BE-01 — Common request, result and execution-mode contract

- Define operation ID, server-derived actor, entity/action/record identity, bounded input, reason, reviewed version, proposal reference and retry key.
- Define outcomes: succeeded, invalid, rejected, conflict, failed and unknown; pending is reserved for an actual host-reported pending operation, never an invented queue.
- Distinguish a committed managed result from work participating in an uncommitted outer host transaction. The UI must not report final success before the applicable commit boundary.
- Keep binding, rendering and redirects in controllers; enforce authorization and execution rules at service boundaries, including direct invocation.
- Keep legacy registration compatibility through an explicit documented adapter. Publish any intentional API/schema changes and migration steps.

Acceptance: HTTP and direct-service tests agree on authorization and outcomes; failure after an external-effect test double is reported as unknown, not as proof of rollback; managed success is only reported after the defined transaction boundary.

### BE-02 — Managed action atomicity and concurrency

- Implement explicit local action execution using the same transaction manager as its entity, success audit and receipt.
- Define supported behavior for versioned edits/deletes/actions, no-field-change actions and deletion races. For mutable existing records without versions, expose a documented limitation or reject the guarded mode; do not silently claim concurrency protection.
- Keep creation separate: it has no pre-existing record version. Replay protection still applies.
- Prevent a handler from changing the authenticated actor, approved input or persisted reason by mutating request data.

Acceptance: independent transactions prove one winner for conflicting versioned changes; stale/missing versions reject before the handler; injected validation/audit/flush failures roll back local state and leave no success receipt/audit. Repeat relevant cases on PostgreSQL 16.

### BE-03 — Durable replay and recovery

- Extend actor-scoped, fingerprinted receipts to explicit managed local actions.
- Define key retention and the supported retry horizon. For the first candidate, no automatic receipt deletion is permitted unless an expiry protocol also prevents expired keys from being executed again. Document storage/operator responsibilities.
- Reauthorize before returning a prior result. Same-key/different-input requests conflict. Concurrent duplicates must not repeat the committed local effect.
- Provide an authorized way to inspect a managed operation result after a lost response. Do not automatically rerun a host/external action with an uncertain outcome.

Acceptance: concurrent duplicates, changed input, revoked permissions and rollback/retry pass; terminate and restart a process against persistent PostgreSQL after commit, then retry the original request and observe one effect, one success audit and the original result. An H2 reseed or two calls in one process is not restart evidence.

### BE-04 — Reviewed proposal enforcement

- Create an immutable server-verifiable proposal bound to actor, action, entity/record, relevant version, normalized input fingerprint and expiry.
- Enforce proposal requirements on actions declared to require review. Recheck permission and eligibility at execution. A review screen alone is not enforcement.
- Define single-use behavior together with replay: a successfully consumed proposal may return its authorized stored result for the same completed request; it must not trigger a second execution. Different requests cannot reuse it.
- Reject tampered, expired, mismatched and stale proposals. Capture any declared related-state dependencies; a root record version does not cover arbitrary related/external state.
- Preview handlers remain trusted, side-effect-free host code. Retain tests proving the real execution handler is not called by preview.

Acceptance: tampered actor/action/record/input, expired proposal, changed version and permission revocation cannot execute; exact completed retries return the existing result; conflicting reuse is rejected. No blanket “safe to execute” claim follows from a valid proposal.

### BE-05 — Audit/outcome accuracy and safe activity projection

- Link operation results and audit entries using operation identity. Preserve actor, time, reason and before/after projections.
- Keep managed success audit atomic. Record rejected/failed attempts separately where supported; do not persist rolled-back success or expose forbidden snapshots.
- Project readable field changes for UI activity. Reapply current exposure/access rules so a field removed or hidden after an older audit was written is not newly leaked by rendering that stored snapshot.
- Do not label ordinary database logs tamper-proof. An external operation's queued/unknown/completed state must come from a real host contract.

Acceptance: committed local state and audit agree; denied viewers/hidden fields are excluded; unknown outcomes remain distinguishable from completed and failed; UI obtains structured permitted data rather than raw entity JSON.

### BE-06 — Existing convenience hardening and bounded export

- Preserve personal saved-view ownership, current permission rechecks, versioned allowlisted state and storage bounds. Verify the PostgreSQL table migration and persistent restart behavior.
- Retain unsaved-input protection across validation, expired sessions and in-flight edits; complete its remaining browser evidence with UI-01/UI-02.
- Implement the already-required export: separate default-deny export permission, the applied filters, explicit permitted columns, bounded rows and bytes, bounded memory, CSV escaping and spreadsheet-formula neutralization.
- Make truncation/limits explicit. Verify unauthorized export, stale permissions, hidden fields, large input/results and cancellation/error handling. Never imply a partial file is a complete dataset.

Acceptance: export is not automatically granted to every viewer; saved views cannot cross accounts; no hidden fields or unneutralized formula cells enter exported output. Row/byte limits are documented and tested. Large asynchronous exports and shared views remain deferred.

### BE-07 — Installation, assets and compatibility

- Add a separate minimal consuming application using built starter artifacts, not reactor-only source assumptions. Cover default/custom admin and context paths, authentication, restricted access, validation, relationship navigation, one versioned action and audit.
- Exercise schema creation/upgrade using migrations against PostgreSQL 16, including receipts and saved views. Document additive-table rollback and the effect of reverting application code.
- Bundle production JavaScript/CSS/fonts locally with versions and license notices. Verify normal operation with external CDN access unavailable; document actual CSP requirements.
- Keep support claims to the tested Java/Spring Boot/PostgreSQL combinations. H2 remains a fast-test/demo environment. Do not claim PostgreSQL 15–17 or MySQL from a PostgreSQL 16 run.

Acceptance: a clean consumer builds and starts from the documented instructions; schema migration and upgrade tests pass; runtime UI has no required CDN fetches; packaged dependency versions and licenses are recorded.

### BE-08 — Evidence and release identity

- Add non-sensitive build/version identity to the demo and tie every hosted result to the actual deployed SHA.
- Capture independent security/transaction review requirements, installation instructions, upgrade notes and remaining limitations. Reconcile superseded documentation rather than treating old findings as current defects.
- Prepare reproducible candidate artifacts/checksums and release notes. Do not claim a Maven Central release until namespace, signing, publication and download verification actually succeed.

Acceptance: a reviewer can map source SHA → CI jobs → packaged artifact → deployed SHA → scenario results. External reviewer and publisher availability are tracked as release gates under the freeze document, not invented as engineering evidence.

## Implementation order

1. BE-01 contract and adapters; independently fix UI-01's misleading state labels.
2. BE-02 and BE-03 managed execution/replay; extend outcome handling without claiming external atomicity.
3. BE-04 proposals, then BE-05 outcome/activity projection and matching UI-02/UI-03.
4. BE-06 export and existing-convenience verification; UI-04 discoverability.
5. BE-07 consumer, migrations and local assets; BE-08 identity and UI-05/UI-06 final verification.

Add tests for guarantees and known failure modes, not test-count targets. Record passes and limitations in [release verification progress](RELEASE_VERIFICATION_PROGRESS.md); use the [completion ledger](IMPROVEMENT_COMPLETION_LEDGER.md) for status.
