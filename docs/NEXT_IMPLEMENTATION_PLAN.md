# Vectis: implementation plan from public preview to a measured pilot

Prepared 28 September 2026. Status: proposed implementation sequence; this document does not mark its work complete.

Scope confirmed by the product owner on 5 October 2026: all stages below are required, including unsaved-edit protection, personal saved views and export. Dependencies still determine implementation order. Pilot feedback informs their design and acceptance criteria; it no longer determines whether these three conveniences are included. External participation, independent review and publishing prerequisites remain explicit gates, not completed engineering work.

1 October: implementation has begun. See [release verification progress](RELEASE_VERIFICATION_PROGRESS.md) for preview diagnosis, new regression coverage and remaining hosted checks.

5 October: the sample now includes a real restricted role and a [permission-denial checklist](RESTRICTED_SAMPLE_ROLE.md), with regression coverage for employee access, denied related targets, unavailable collections and search exclusions. This implements part of E2 ahead of the mutation stages because it is independent of those contracts. It does not complete E1 or the full E2 security matrix.

This is the current execution order for remaining work in [the original roadmap](../VECTIS_IMPROVEMENT_PLAN.md). Keep that roadmap and milestone reports as historical context. Where earlier documents describe completed features as pending, reconcile them against implementation and tests before changing claims.

## 1. Outcome and boundaries

Enable an external Spring Boot/JPA team to install Vectis, expose one recurring support operation, and let an authorized operator complete it with a verifiable result. Measure whether the workflow reduces engineering interruptions and is used again.

The first pilot targets a Spring Boot/JPA application with a repeated record lookup or reversible support operation. Select its actual task with the pilot team. Use the existing leave-status sample for engineering regression tests, not as evidence that customers need an HR product. Start external evaluation on synthetic or staging data. Production writes require a separate readiness decision.

Keep Java 17, Spring Boot, JPA, server-rendered templates, HTMX, Alpine and host-owned authentication. No frontend rewrite, raw SQL execution, automatic AI remediation, cross-framework support, multi-application control plane or Invariant integration in this cycle. Multi-tenant isolation, granular field RBAC and approval workflows are not implicit promises.

## 2. Baseline and evidence

| Area | Current evidence | Remaining gap |
|---|---|---|
| Distribution | Public `yavonalabs/vectis`; source build; Apache 2.0; GitHub CI | No Maven Central release; external fresh-app installation unproven |
| Demo | `https://demo.vectis.yavonalabs.com` resolves to Render; HTTPS HTTP smoke passed for both accounts | Full browser/mobile/accessibility pass on final domain; exact deployed SHA for each pass |
| Access | Host security integration, entity/action evaluator, hidden-field protections, related-record checks | Restricted sample role to independently exercise target-entity denial; no tenant-isolation claim |
| Preview | Explicit preview handler, proposed visible scalar changes, validation; real handler not used for preview | Developer code is trusted, associations are shallow-copied; policy for requiring a reviewed preview at execution |
| Mutations | Create/edit/delete/actions and reason capture work in tested paths | Shared execution lifecycle, atomic audit, complete concurrency checks, durable retry deduplication |
| UX | Branded components, typed filters, bounded relationships, preserved list context | Hosted focus regression verification, unsaved-edit protection, measured accessibility gaps |
| Validation | Latest local Maven verification: 63 passing tests; focused browser checks | Passing tests do not establish all lifecycle or production guarantees |
| Adoption | No customer evidence established in this work | Independent setup, repeated real workflow use, willingness to pay |

Recent specific findings: `_version` is checked for actions only when supplied; no durable idempotency protocol is established; audit writes use `REQUIRES_NEW` and are not atomic with mutations; local action preview returned Forbidden during the focus check. These need scoped fixes or diagnosis, not reassuring copy.

## 3. Delivery sequence and estimates

Estimates are developer effort, not elapsed-time commitments. Assume one developer, a reviewer for transaction/security changes, and pilot participants available separately. Re-estimate after lifecycle design and the first independent installation. Do not reuse the original roadmap's estimate as work still remaining.

| Stage | Effort | Dependency | Exit deliverable |
|---|---:|---|---|
| A. Close demo verification and docs | 3–5 days | Existing deployment | Reproducible verification evidence; honest public preview page |
| B. Define mutation contracts and extraction | 3–5 days | Baseline behavior captured | Shared lifecycle for CRUD/actions with documented transaction modes |
| C. Atomic mutation and audit | 4–6 days | B | Failure-injection tests prove selected transaction guarantees |
| D. Concurrency and idempotency | 5–8 days | B, C | Stale/duplicate requests cannot silently reapply the pilot mutation |
| E. Preview, policy and permission proof | 3–5 days | B–D | Reviewed proposal tied to execution; independently testable denial paths |
| F. Integration and release package | 4–7 days | B–E | Fresh-app installation and supported database checks; versioned prerelease |
| G. Pilot and distribution | 2–4 days setup + 2–3 weeks observation | A for recruitment; F for installs | Recorded adoption/effort evidence and explicit continue/change/stop decision |
| H. Operator conveniences | 3–6 days per slice, subject to re-estimation | B–E for writes; workflow feedback for design | Unsaved-edit protection, personal saved views and bounded permission-aware export |

Stages A–F total roughly 22–36 developer days before a stronger external installation candidate. Discovery can run alongside engineering. Pilot observation and review availability add calendar time. H is not a prerequisite for the first pilot.

## 4. Stage A — Finish the public preview responsibly

### A1. Resolve preview Forbidden and verify the actual deployed build

- Reproduce with a fresh login on local sample and final HTTPS domain; capture action ID, route, role, HTTP status, request method and presence of CSRF without logging token values.
- Distinguish expired session, CSRF rejection, application permission denial, method-security denial and incorrect action registration. Do not relax authorization to make the demo work.
- Verify a permitted preview, denied preview, invalid projected state and missing preview configuration. Confirm the real handler and external side-effect test doubles are never invoked by preview.
- Record deployed Git SHA from Render. Add non-sensitive build version/short SHA to sample diagnostics or footer, sourced by the build; do not expose environment variables or private deployment details.
- Recheck the dialog focus fix from commit `6782d2c` on the host: Escape, Cancel, action and delete dialogs, different rows, and failure states.

Done when: permitted preview works after fresh login, denial remains correct, and the verified build has a recorded SHA. No stale-session explanation is accepted without reproduction evidence.

### A2. Hosted verification matrix

Use desktop and 320/375/768px layouts on the custom domain. Cover login/error/logout, overview, every navigation destination, filters/no results, related collections, create/edit validation, drawer, preview and delete cancellation. Use fictional records only; cancel destructive checks. Execute one reversible sample action and verify its actual state and activity.

For keyboard access, verify visible focus, logical order, modal containment and return, Escape, mobile navigation and table-scroll access. Measure text/control contrast including errors and focus states. Test reduced motion, 200% zoom and a screen reader's labels/errors/status announcements. Automated DOM checks supplement rather than replace the screen-reader pass. Record unavailable tooling or failed checks explicitly.

Run `scripts/smoke_demo.py` against the final domain. Verify session expiry, restart/reseed and account permissions. Arrange the disposable demo restart after recording test evidence; warn about temporary interruption and loss of fictional sample changes. Confirm secure cookies and no H2 console.

Done when: every matrix row records build, environment, result and evidence; release-blocking failures are resolved and affected paths retested. No claim of a full accessibility audit unless that audit was actually performed.

### A3. Product page and documentation

- Reconcile README, SAFE_ACTIONS, DEPLOYMENT, HOSTED_DEMO_VERIFICATION and product-page notes. In particular, remove obsolete claims that GitHub/custom domain are not configured and distinguish implemented filters/UI from pending work.
- Add a compact implemented / partial / planned table. Document preview trust, mutation limits and sample resets beside relevant guidance.
- Prepare a short installation walkthrough, real screenshots and one complete task example. Show what the operator achieved, not just a feature list.
- After A2 passes, publish the static product page at `vectis.yavonalabs.com` using the agreed existing host or a reviewed static-host configuration. Verify hosting ownership before adding DNS. Keep `demo.vectis` DNS-only for Render HTTPS; preserve root and mail records.
- Verify canonical URLs, title/description, source/demo links, mobile layout and keyboard access on the published page. Search indexing and rankings are observations, not release guarantees.

Done when: a stranger can find the repository, understand the supported use case, open the verified demo and follow source setup without hidden instructions. Publication can advertise an early preview; it must not imply production mutation readiness.

## 5. Stage B — Establish one mutation lifecycle

Primary implementation locations: `AdminController`, `ActionPreviewController`, `EntityActionRegistry`, `EntityAction`, `DynamicCriteriaQueryEngine`, `AdminPermissionEvaluator`, and new services under `core/mutation`.

### B1. Write a short architecture decision before coding

The [mutation lifecycle decision](MUTATION_LIFECYCLE_DECISION.md) records the current transaction gaps, selected execution modes, service boundary and required failure evidence. This completes the initial design document, not the extraction or atomicity milestones.

Define mutation request/result contracts covering operation ID, actor, entity/action identity, validated input, reason, expected version and idempotency key. Derive actor from authenticated server context. Bound all fields; never accept an actor or permission decision supplied by the browser.

Define the sequence: authenticate → authorize entry → validate request → load permitted current record → check expected state → check business eligibility → execute → validate result → persist → record outcome → commit → return result. Permissions must also be enforced when the service is invoked directly rather than through its controller.

Separate two supported modes:

1. Vectis-managed local JPA mutation: entity mutation, success audit and deduplication result share one database transaction.
2. Service/external-effect operation: host integration defines transactional boundaries and dispatch behavior. Independent transactions or external HTTP/email effects cannot inherit the local atomicity claim. Unsupported combinations are rejected or clearly outside the pilot contract.

### B2. Extract services with compatibility adapters

- Move save/delete/action orchestration out of controllers; keep HTTP parsing, validation feedback and redirects in controllers.
- Preserve supported custom paths, servlet context paths, list context, ordinary forms and HTMX responses.
- Keep existing annotations/registration working through adapters where possible. Publish migration instructions for deliberate contract changes.
- Route generic create/edit/delete through the same authorization/result conventions. Explain that generic edits do not automatically invoke domain services.
- Produce structured outcomes: succeeded, rejected, conflict, validation failure, failed, and pending/unknown where an external effect cannot yet be established. Do not blindly invite retries after ambiguous results.

Done when: existing regressions pass and both controller and direct-service tests prove policy enforcement. The transaction ownership of every supported operation type is documented.

## 6. Stage C — Make mutation and audit agree

- For managed JPA mutations, write the success audit within the same transaction and transaction manager as the entity write. Replace the current independent success-audit path for this mode.
- Flush before constructing the final persisted outcome as required; snapshots must reflect committed-intent values and exclude ignored/unauthorized fields.
- If success audit persistence fails, roll back the managed mutation. Do not catch the exception and return success.
- Use a separate attempt record only for rejected/failed attempts, clearly distinguished from successful changes. Avoid storing forbidden record data or raw exception/request payloads in failure records.
- Define operation ID, actor, time, entity identity, action, reason, before/after projection and result. Set bounded retention/export guidance and redact sensitive fields; do not call ordinary database logs tamper-proof.
- For external effects, prefer a host-owned transactional outbox and downstream idempotency where applicable. Record queued versus delivered outcomes honestly. Do not claim exactly-once external execution.

Acceptance tests: entity constraint failure produces no success audit; audit insert failure leaves entity unchanged; successful managed operation creates one matching audit; rollback after flush preserves neither success record nor mutation; a failed attempt cannot masquerade as success; unauthorized audit viewers cannot retrieve snapshots.

Done when: the same tests pass against PostgreSQL, not only H2. If local Docker is unavailable, execute database-backed tests in CI rather than silently skipping them.

## 7. Stage D — Prevent stale and duplicate operations

### D1. Concurrency

- For versioned records, require a valid expected version on update/delete/action requests covered by the reviewed-state contract. Missing/malformed input is a rejection, not an unchecked fallback.
- Use transactional optimistic locking and verify the final write; the current read-then-compare alone is insufficient. Include no-field-change actions and delete behavior in the locking decision.
- Define policy for unversioned entities explicitly. The initial guarded pilot requires versioning; do not invent lost-update protection where none exists.
- Return a conflict with preserved reason/input and a clear refresh/review path. Never auto-retry an operation against new state without operator review.

Acceptance: two independent transactions acting on the same version produce one accepted mutation and one conflict; stale delete/action is rejected; omitted version cannot bypass the check; no external effect is emitted by the losing local transaction.

### D2. Durable idempotency

- Introduce a database-backed operation table and unique key constraint. Scope keys to actor and operation identity; bind a canonical request fingerprint to the key.
- Issue a key for the confirmation attempt and reuse it for retries of that same request. Changed payload with the same key is rejected. Reauthorization is required before returning an old result.
- Reserve and finalize local-operation results transactionally with the mutation and success audit. Handle concurrent duplicate requests at the database constraint boundary, not with a process-local map.
- Define expiry and cleanup; retain records beyond the supported retry window. Define what the client sees while a request is running or its external outcome is uncertain. Do not automatically rerun unknown external operations.

Acceptance: concurrent duplicate submissions, lost HTTP response followed by retry, process restart, key reuse with different payload, and unauthorized replay. One local business effect and one success audit must result for the same accepted request.

Done when: tests exercise multiple connections and failure timing. Disabled submit buttons remain useful UX but are never the deduplication guarantee.

## 8. Stage E — Align previews, permissions and execution

### E1. Explicit proposal contract

- Define typed action input and a proposal object with action/record identity, expected version, input fingerprint and visible proposed changes. Prefer immutable scalar projections over passing mutable entity graphs in new preview APIs.
- Add an explicit action policy for requiring a reviewed proposal. Validate it server-side on execution, binding it to actor, action, record, input and version; define expiry. Browser confirmation alone is not enforcement.
- Recheck permission, version and eligibility at execution. A proposal cannot grant access or prove that state remains unchanged.
- Retain a documented adapter for existing preview methods; state that trusted handler code can still call external systems. Migration does not create a sandbox.
- For permitted execution without preview, use an explicit policy and honest UI; otherwise fail closed. Show actual outcome independently of proposed changes.

Acceptance: tampered/reused/expired proposal, changed input, changed actor, revoked access, stale version, preview failure and invalid projected state. Assert the real mutation handler was not invoked during preview.

### E2. Independently testable permission boundaries

- Add a documented sample role allowed to view employees but denied department details, searches and collections. Use fictional public demo credentials only.
- Verify denied related targets show unavailable rather than an empty collection. Cover list, detail, drawer, relationship labels/options, search, direct URLs, preview, execution and audit.
- Test unsupported policy expressions fail closed and Spring-proxied service actions preserve their method authorization.
- Do not label entity-level permission tests as row-level tenant isolation.

Done when: another reviewer can reproduce both allowed and denied journeys using the sample, without relying solely on internal integration tests.

## 9. Stage F — Make installation and upgrades repeatable

- Create a separate minimal Spring application fixture that consumes the built starter as a dependency. Do not rely only on the same-repository sample.
- Verify default/custom admin path, context path, authenticated/read-only/restricted users, one entity, relationship, versioned action, validation and audit.
- Define a tested Spring Boot/Java matrix before claiming support. Keep initial claims narrow; broader compatibility requires actual CI coverage.
- Run PostgreSQL integration tests with schema migrations for operation/audit storage. Add other databases only where a pilot requires them and tests exist.
- Bundle production UI dependencies locally with license notices and versions. Remove runtime CDN assumptions; verify operation without access to those CDNs and document CSP requirements.
- Publish reproducible setup, permission and domain-service examples; explain exactly where host authentication and transaction responsibilities begin.
- Prepare a tagged prerelease, release notes, migration/rollback notes, artifact checksums and license/dependency review. Configure signing/publishing credentials through approved secret storage, never source control.
- Publish to Maven Central only after account/namespace ownership, packaging and release checks succeed. Until then, retain honest source-build instructions. Do not treat tagging as package publication.

Acceptance: a developer unfamiliar with Vectis completes one configured staging workflow following public docs. Measure time and assistance needed; 30 minutes is a target, not a claim. Upgrade an earlier fixture and verify schema/data compatibility. Rollback guidance must distinguish application rollback from database migration reversibility.

## 10. Stage G — Test demand with a small external pilot

Product owner leads recruitment, workflow selection and commercial conversations; engineering supports setup and measures friction. External messages/posts require separate approval before sending.

1. Prepare a short task-focused demo, install guide, known limitations, setup-help issue template and pilot feedback form. No automatic telemetry or public customer data collection.
2. Seek three Spring Boot teams through relevant developer communities, existing contacts and opt-in setup requests. Public content should explain one real problem and show evidence; do not promise top search ranking.
3. For each team, record its current task, frequency, engineer time, existing alternative, required permissions and acceptable failure behavior. Agree the baseline before installation.
4. Install on synthetic/staging data; configure one reversible action. Have the operator complete the task without live coaching, then observe voluntary repeat use over two weeks.
5. Ask what the team would pay for and why: setup help, supported upgrades or another explicitly requested service. Do not commit to $1 pricing or an enterprise edition before testing costs and demand.

Decision targets (proposed, not existing results): three independent installations; at least two teams repeat the workflow in both observation weeks; no unresolved critical correctness/security defect; each active team reports actual baseline versus assisted task time; at least one concrete paid-pilot or support commitment before claiming willingness to pay.

If setup repeatedly fails, prioritize integration before more features. If installation succeeds but repeat use does not occur, revisit the workflow/customer segment. If teams use it but decline payment, investigate buyer/value/support scope rather than interpreting stars as revenue. Stop broad expansion if there is no repeated need after these interviews and trials.

## 11. Stage H — Required operator conveniences

| Slice | Implementation boundary | Acceptance |
|---|---|---|
| Unsaved-edit protection | Initial dirty-state tracking, navigation warning, HTMX and session-expiry interaction | Cancelled navigation preserves input; successful save clears dirty state; no false warning after save |
| Personal saved views | Versioned allowlisted filter/sort/page-size schema; owner-scoped persistence; reapply permissions when loaded | No shared secrets or unauthorized fields; removed fields handled; malformed/oversized state rejected |
| Export | Separate permission, same filters and safe projection, row/size bounds, spreadsheet formula neutralization | Hidden fields absent; access rechecked; bounded memory; cancellation/limits and cell interpretation tested |

Deliver all three slices, starting with unsaved-edit protection, then personal saved views and bounded export. Use workflow feedback to refine each design without dropping it from the agreed scope. Export is not automatically available to every read-only user. Shared views and large asynchronous exports remain outside this scope until their ownership/permissions model is established.

## 12. Verification, release and ownership rules

- Each slice includes implementation, meaningful regression tests, operator feedback where relevant, documentation and a recorded known-limitations update.
- Run `mvn verify` for Java/template changes. Use browser tests for focus, HTMX and responsive behavior; Java test counts alone cannot validate these.
- CI should run real database concurrency/failure tests and publish clear results without credentials or sensitive fixtures. A skipped environment-dependent test is not a pass.
- Use small reviewable commits/PRs; changes to permission/transaction behavior require independent review before pilot production writes. The coding agent cannot substitute self-review for independent assurance.
- Keep GitHub and the GitLab Render source synchronized intentionally; verify exact SHAs and deployment results. Group documentation changes to avoid unnecessary demo restarts from auto-deploy.
- Release record: commit, automated tests, browser/device matrix, database versions, known limits, upgrade notes and rollback path. Public docs must separate implemented, partial and planned behavior.

## 13. Immediate work queue

1. Update hosted evidence with the already-passed custom-domain DNS/HTTPS/account smoke results.
2. Diagnose the preview Forbidden response and recheck hosted dialog focus on the deployed fix.
3. Complete the final-domain mobile/accessibility matrix; publish the early-preview page when that gate passes.
4. Write the transaction/operation contract decision and failure-case tests before lifecycle extraction.
5. Implement managed mutation plus atomic audit, followed by version enforcement and durable idempotency.
6. Add restricted sample role and reviewed-proposal execution policy.
7. Validate a fresh consuming app and publish a verified prerelease; begin measured external pilots.

Do not delay discovery conversations until all engineering work is done. Do not use discovery interest as permission to skip mutation guarantees.
