# Release verification progress — updated 9 October 2026

## 9 October hardening candidate (not frozen)

GitHub run [37951022440](https://github.com/yavonalabs/vectis/actions/runs/37951022440) passed both jobs on `0919e79b06ba44fb7d085c5fa7cd10f4ab26851d`: full Maven/JavaScript/asset-reproducibility verification and PostgreSQL 16 mutation tests, SQL migrations, independent consumer packaging and actual process restart/replay. The first run failed before consumer startup because a clean checkout lacked the root log-output directory; `0919e79` creates that directory and the rerun passed. The custom-domain demo's authorized build endpoint returned that exact SHA. Fresh hosted HTTP smoke passed all three sample roles (`target/freeze-hosted-http.log`, `target/freeze-hosted-identity.log`).

The follow-up working candidate adds a readable recovery-result page, visible form references, shorter action lists and persistent saved-view restart coverage. Full local verification passed again with 117 Java and 19 JavaScript tests (`target/freeze-recovery-ui-verify.log`, `target/freeze-recovery-js.log`); the new consumer scenario and later CI/deployment remain separate gates until recorded. Local 375px login/form/navigation, dirty-form navigation cancellation, tab arrow keys, dialog Escape/focus restoration and 320/768px list overflow checks passed on the upgraded package. A rendered-detail text check covered 63 text elements without AA contrast violations; this limited check does not certify all screens, control contrast, zoom or screen-reader behavior.

The follow-up consumer run passed with persistent saved-view reopening/cleanup and a third process at the default root context and `/admin` path (`target/freeze-default-consumer.log`). Browser inspection confirmed compact action cards, a visible save reference and an unconfirmed-result page while retaining a draft in the original form. The in-app browser did not expose a popup after the new-tab link click; the result URL was opened explicitly to inspect it. That is not evidence that popup behavior passed across browsers. No business mutation was submitted in this follow-up UI inspection; the draft was restored and cancelled. Screenshot: `target/freeze-demo/recovery-result.png`.

`mvn -B -ntp verify` passed on Java 17 / Spring Boot 3.5.16: 117 Java tests, zero failures/errors/skips. `npm test` passed all 19 JavaScript tests; `npm run build:assets` rebuilt the bundled assets. Local logs: `target/freeze-boot35-recheck.log`, `target/freeze-js-final.log`, `target/freeze-assets-final.log`.

The first upgraded run found ten rendering errors at the form layout's title expression. Removing the restricted expression from that fragment argument fixed them; the full repeated suite passed. New typed-adapter tests verify managed commit/replay, conflict, unknown host outcomes, outer-transaction rejection and conflicting save identities. The independently packaged consumer also passed on 3.5.16: custom context/admin paths, CSRF, managed commit, receipt recovery, actual two-JVM persistent-H2 restart, exact retry with unchanged version/effect, one audit, schema validation and local asset access. Evidence: `target/freeze-consumer-final.log` and `target/consumer-verification.log`. Current PostgreSQL CI remains separate evidence.

Earlier local browser checks covered admin login, record lists/details, accessible tab selection, managed review/submit and CSV download. List geometry was checked at 320/375/768/1280 pixels with contained table scrolling. These checks used the earlier local candidate and do not certify the final hosted artifact, contrast/zoom or a real screen reader. See the completion ledger for outstanding engineering acceptance and the freeze boundary for external release gates.

The entries below are historical evidence for their respective builds, not a declaration that older open findings remain unfixed or that later builds inherit their verification.

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

### Managed CRUD receipts — 7 October 2026

Added database-backed operation receipts scoped to authenticated actor and request key. CRUD reserves the key and commits its result in the same transaction as the record and audit. Completed identical requests return the stored result; changed input rejects. Current permissions are rechecked before replay. Forms preserve the key for an attempt; delete confirmation creates one on opening.

Full Maven verification passed with 88 Java tests after correcting a Mockito varargs/generic reset in the rollback-retry test. Coverage includes create/update/delete replay, concurrent duplicates, changed input, revoked permissions and rollback followed by retry. The eight existing JavaScript tests passed in the preceding run; final browser verification of operation keys, PostgreSQL CI and actual process-restart testing remain open. The prior hosted versioned CRUD check passed and removed its unique fixture.

See `MANAGED_REQUEST_REPLAY.md` and `sql/mutation-receipts-postgresql.sql` for migration, retention, request bounds and custom-action exclusions. No automatic expiry/cleanup is implemented. The full required scope is tracked in `IMPROVEMENT_COMPLETION_LEDGER.md`; it is not complete.

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

### Personal saved views — 7 October 2026

The owner reported GitHub and Render passing for commit 3f4c90e before this slice. That report does not verify subsequent commits.

Implemented personal saved views with server-derived ownership, permission rechecks, schema validation, bounded filter/name values and 50 database-enforced slots per owner/section. Added source migration notes and a PostgreSQL table example. Read-only operators may manage their own views; record-write permissions remain unchanged.

Local browser checks on port 18086 used the fictional read-only account to apply the Alice search, save it, clear the current filters, reopen the saved view and remove it. Reopening restored one matching record; removal left all four sample records intact. A browser-observed missing confirmation message was corrected and covered with context-path/redirect tests. The initial view-name input lacked shared styling; the panel now uses the existing styled form classes.

Also corrected untouched edit forms being marked dirty because submittedValues defaults to an empty map. A rendered-form regression test covers the false marker. The roadmap still requires hosted/mobile verification and actual restart persistence; H2 tests and a SQL example are not PostgreSQL migration evidence.

Final local verification for this slice passed: `mvn -B -ntp verify` ran 98 Java tests with no failures/errors/skips; 17 JavaScript tests passed. The saved-view suite covers account isolation (including administrator denial), forged-owner input, revoked entity access, CSRF, malformed/oversized state, obsolete fields/schema versions, quota reuse, applied-state capture and untouched-form dirty state. A separate custom-path test covers create/open/remove and confirmation-message propagation. The expanded PostgreSQL CI job has not yet been observed for this commit.
