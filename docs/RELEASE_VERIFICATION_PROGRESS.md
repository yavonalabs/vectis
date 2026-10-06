# Release verification progress — updated 5 October 2026

This records the first implementation slice of NEXT_IMPLEMENTATION_PLAN.md, not completion of its release gates.

## Preview diagnosis

Fresh login against the existing local packaged sample on port 18083 successfully rendered the leave-status preview in a real browser. Separate real-HTTP requests using the rendered CSRF token returned 200 for both multipart and URL-encoded preview bodies, and 403 without CSRF. The earlier Forbidden response is not reproduced; its cause remains unconfirmed. No authorization or CSRF policy was relaxed.

The frontend now distinguishes 401/sign-in redirects, 403 denial and non-JSON server responses rather than exposing a raw Forbidden or JSON parsing error. Each failure clears the old proposal and leaves confirmation disabled. A 403 is not described as proof of session expiry.

## Verification added

- `node --test scripts/action-modal.test.cjs`: six tests against the actual actionModal controller cover success/reason gating, 401, redirected HTML login, 403, HTML server failure and JSON validation error.
- `python scripts/smoke_demo.py http://localhost:18083`: passed with additional preview checks using real login sessions and rendered CSRF tokens. Admin preview succeeds; read-only preview and missing-CSRF requests fail. These are endpoint checks against the running sample, not proof that its packaged JavaScript includes the new error messages.
- Fresh local browser preview at 375 × 812 shows proposed status, reason and confirmation controls without page-level horizontal overflow. This is not final-domain mobile verification or a full accessibility audit.
- A custom-domain HTTPS diagnostic timed out on 1 October before login. The previously recorded successful HTTPS smoke remains historical evidence; it is not a fresh availability pass.

## Remaining

### Required edit/delete versions — 6 October 2026

Versioned edits now require the form's version field; deletes require `_version` from the reviewed table row or detail. Missing/malformed versions return 400 and stale versions return 409. Delete confirmation replaces its hidden version when switching records, preserving zero. Unversioned entities retain their existing behavior without a lost-update guarantee.

The atomic-mutation suite adds missing/malformed/stale input checks, a delete following a newer edit, and two simultaneous service calls using separate transactions after both have loaded the same version. The H2 concurrency test produces one successful update, one optimistic-lock failure and one success audit. The same class runs in the PostgreSQL CI job; its new run remains pending. Eight JavaScript tests pass, including delete-version replacement.

Final full Maven verification passed with 83 Java tests and no failures, errors or skips.

This does not complete stage D: action transaction-level concurrency, durable idempotency and preserving submitted inputs throughout the conflict/review journey remain open. Hosted browser verification of these new version controls is also pending.

### Required action versions — 6 October 2026

The user reported successful Render and GitHub jobs for the managed CRUD milestone, including the PostgreSQL job previously requested for validation. That result was reported by the user, not independently retrieved from CI in this turn.

Versioned actions now reject missing or blank `_version` with 400 and mismatches with 409 before invoking the handler. The confirmation form now preserves initial version zero instead of replacing it with an empty string. Full Maven verification passed with 80 Java tests; seven JavaScript tests passed, including the actual template binding at version zero. Required edit/delete versions, transaction-level concurrency protection for actions and durable idempotency are still pending.

A new explicitly invoked `scripts/smoke_demo_mutations.py --allow-mutations` check creates one uniquely named fictional record on the fixed public-demo host, verifies its edit/audit reasons and removes it in cleanup. The first hosted attempt timed out on the login page before any mutation; subsequent results are recorded separately below.

The retry passed: created disposable employee 5, independently read it back, updated its name, confirmed both create/edit audit reasons on the detail page, deleted it and verified that the detail URL returned 404. No fixture remains from the successful run. This verifies hosted CRUD behavior on the preceding deployed build; the required-action-version change still needs deployment verification.

### Managed CRUD transaction — 6 October 2026

Create/edit/delete now enclose the entity write and direct success-audit write in one default JPA transaction. Audit persistence failures propagate. Save flushes before projecting the final state; supplied versions are compared rather than assigned onto a managed existing entity. Legacy action auditing remains unchanged. CRUD no longer publishes the legacy audit event; see `ATOMIC_RECORD_MUTATIONS.md` for compatibility and transaction-manager limits.

Full Maven verification passed: 79 Java tests, including seven new tests without test-owned transactions. H2 checks cover successful CRUD audits, rollback after an audit flush for all three operations, a real audit-column failure and an entity uniqueness failure. The first injection setup incorrectly stubbed a transactional proxy; targeting its underlying spy corrected the harness without weakening transaction enforcement.

PostgreSQL 16 CI coverage is configured, but has not run: no local Docker/PostgreSQL executable was available and remote authentication was previously unavailable. PostgreSQL evidence, independent review and deployment verification remain open; stage C is not complete. Custom actions, durable idempotency and mandatory version/proposal enforcement remain separate work.

### Record service extraction — 6 October 2026

Create/edit/delete now delegate to `RecordMutationService`. The service resolves the actor from server context, checks console/entity/write permission and enforces related-entity permissions. Invalid records carry their submitted entity and validation errors back to the controller; form feedback, reasons, list context and HTMX redirects remain covered by existing regressions.

Full Maven verification completed successfully with 72 Java tests, zero failures, errors or skips. New direct-call tests cover missing authentication, restricted save/delete and denied relationship updates. The preceding deployed version passed the three-account hosted HTTP smoke; this extraction has not yet been verified on Render. This step preserves the existing independent audit transaction and does not establish atomicity, mandatory concurrency checks or durable idempotency.

### Action service extraction — 5 October 2026

The deployed demo passed the full three-account HTTP smoke before this extraction. Action execution now delegates to `ActionMutationService`, with server-context actor resolution and console/entity/action permission checks at its direct entry point. Added direct-service tests for absent authentication, restricted access despite forged actor input, denied entity access, missing reason and stale version.

Full Maven verification passed with 69 Java tests after correcting the optional Spring Security adapter's module placement. HTTP action routes and existing custom-path regressions pass. The extraction itself has not yet been verified on Render. CRUD orchestration, atomic audit, mandatory version enforcement and durable idempotency remain pending. See `MUTATION_LIFECYCLE_DECISION.md` for manual-controller construction compatibility and custom-authentication requirements.

### Hosted restricted-role verification and lifecycle design — 5 October 2026

After the user reported the Render build passed, the first hosted smoke attempt timed out during admin sign-in. A subsequent complete run against `https://demo.vectis.yavonalabs.com` passed for admin, user and restricted, including relationship redaction, unavailable collection text, denied entity routes, search exclusions and logout. This verifies the restricted-account HTTP behavior live; it does not establish an exact deployed SHA or a full browser/accessibility pass.

The mutation lifecycle decision now documents transaction ownership and the required failure/concurrency evidence. Unexpected action errors no longer invite an unconditional retry. A regression simulates an external effect followed by failure and checks that only uncertainty guidance is returned. Atomic audit and durable deduplication are still unimplemented.

Full Maven verification passed after this change: 65 Java tests, zero failures, errors or skips. The new guidance is verified by that integration test; hosted browser verification of this subsequent change is pending deployment.

### Restricted sample role — 5 October 2026

Added `restricted / password` with employee-only read access and a manual checklist in `RESTRICTED_SAMPLE_ROLE.md`. Full `mvn verify` passed with 64 Java tests; six JavaScript tests also passed. The freshly packaged demo on port 18084 passed the expanded HTTP smoke for all three accounts, including real restricted login, hidden department labels, unavailable skills collection, denied direct routes and excluded search results. An initial smoke attempt preceded server readiness and was rerun after startup completed. Hosted verification of this new role remains pending deployment.

### Hosted follow-up — 5 October 2026

After the user reported a successful Render deployment, the complete HTTP smoke script passed against `https://demo.vectis.yavonalabs.com` for both sample accounts, including preview permissions, missing-CSRF rejection, restricted activity and logout/session invalidation.

Browser checks at 375 × 812 covered sign-in, opening mobile navigation, navigating to Team Members and previewing Alice's leave-status change. The measured login and record-list document widths did not exceed the viewport. The preview displayed Active → On leave and kept confirmation disabled without a reason. Escape closed the dialog and returned focus to the Record actions button.

Logging out in a second tab and reopening preview in the stale first tab displayed the new access-denied guidance, removed the previous proposed change and disabled confirmation. This verifies the updated frontend behavior on the custom domain; the exact Render commit SHA was not independently inspected. No business mutation was submitted.

These checks supersede the earlier availability timeout and satisfy the targeted deployed-preview check. They do not constitute a full mobile visual, contrast or screen-reader audit.

The full Maven reactor verification completed successfully on 1 October after stopping the local demo process that held the packaged JAR open on Windows. This was a packaging lock, not a test failure. The six JavaScript regression tests passed again on 5 October and now run in GitHub CI alongside Maven verification.

Recheck exact deployed SHA and complete the remaining hosted mobile/keyboard/contrast/screen-reader matrix before promoting the public demo. Diagnose the historical Forbidden only if reproducible in a fresh authenticated session. No product-page publication is claimed by this slice.
