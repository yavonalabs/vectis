# Code-freeze boundary: first verified developer-preview candidate

Date: 7 October 2026. Baseline: `4ba4d57`. Owner: YavonaLabs product owner. Engineering: current Vectis implementation workflow.

**Scope is bounded now. Code is not frozen yet.** Freeze occurs after the finite backend/UI packages below meet their engineering acceptance criteria. This document defines the boundary; it does not certify the current build or authorize production writes.

## 1. Release objective and controlling documents

Deliver a Spring Boot/JPA developer preview for finding records, performing explicitly supported operations, reviewing accurate outcomes and repeating a saved workflow. Preserve Java 17, the current Spring Boot/server-rendered/HTMX/Alpine architecture and the approved Vectis visual identity.

The controlling work lists are [BE-01–BE-08](BACKEND_REVIEW_ACTION_PLAN.md) and [UI-01–UI-06](UI_REVIEW_ACTION_PLAN.md). The [completion ledger](IMPROVEMENT_COMPLETION_LEDGER.md) records status; [release verification progress](RELEASE_VERIFICATION_PROGRESS.md) records evidence. The older implementation plan remains context for dependencies and external pilot work. Its historical baseline tables are not the current implementation status.

Previously required unsaved-edit protection, personal saved views and bounded export remain included. No required convenience is silently dropped to achieve a freeze. External pilots and publishing prerequisites remain obligations, but are tracked separately from feature development.

## 2. Included boundary

| Area | Finish before declaring code freeze |
|---|---|
| Mutation correctness | Explicit managed/host execution modes; shared request/result semantics; managed action atomicity and concurrency; durable replay and authorized recovery; proposal enforcement; truthful outcome/audit projection |
| Existing conveniences | Close unsaved-input/saved-view gaps; implement bounded, separately permissioned export |
| Record workflow | Truthful unavailable states; usable review/outcome flow; grouped detail presentation; action hierarchy; accessible activity; applied-filter chips and personal-view discoverability |
| Presentation | Consolidate existing tokens/components; preserve approved branding; no visual restart |
| Installation | Clean consuming app; tested PostgreSQL 16 migrations/upgrade/restart; locally bundled runtime assets; honest supported-version/CSP documentation |
| Verification | Exact build identity; required automated checks; local and hosted mobile/keyboard/accessibility matrix; reproducible candidate artifacts and release notes |

All included changes must support this finite workflow. Do not add an unrelated feature because a reviewer mentions it or another admin tool has it.

## 3. Excluded until a later cycle

- Team/shared views, cross-account sharing and workspace administration.
- Bulk mutations, mass approvals and large asynchronous exports.
- Query-language/AI/natural-language builders or raw SQL execution.
- User-persisted column layouts, dark mode, analytics dashboards or additional cosmetic redesigns.
- New frameworks, database families or a broad untested version matrix.
- SSO as a separate product, granular field-RBAC product, tenant-isolation claims or approval workflow engine.
- Universal payment/email/queue/outbox integrations or universal exactly-once external-effect claims.
- Monetization, billing, SEO ranking guarantees and product expansion unrelated to the selected pilot workflow.

These are deferred, not rejected forever. Keep them in a later backlog; they cannot enter the candidate through “small polish” commits.

## 4. Three distinct checkpoints

### A. Scope freeze — this document

The package list is closed. Implementation, tests and fixes inside that boundary continue. New reviews may reveal a defect in the agreed guarantees; they do not automatically authorize more features.

### B. Code freeze — engineering candidate complete

Declare a candidate SHA only when all of these are true:

- Every BE/UI package has evidence against its acceptance criteria. No “implemented” checkbox substitutes for its tests and verification.
- The complete Maven and JavaScript suites, PostgreSQL jobs, migration/upgrade/restart checks and clean-consumer workflow pass on the candidate. No skipped required test is counted as passed.
- The exact hosted build passes the UI matrix, including real screen-reader and mobile checks. Untested required entries remain blockers.
- No unresolved blocker: unauthorized exposure/mutation, duplicated committed local effects, lost edits/data, incorrect success/outcome reporting, incompatible required migration, broken install/login/core workflow or inaccessible core task.
- Remaining minor defects are explicitly listed with impact and workaround; they cannot conceal a failed required acceptance criterion. The product owner accepts any proposed exception in writing.
- README, public claims, schemas, release notes and known limitations agree with the candidate. GitHub and the Render-source GitLab remote resolve to the intended SHA; deployed identity is recorded separately.
- A freeze record is completed. Do not label a build frozen merely because CI is green or development has taken longer than expected.

After this point, feature work stops on the candidate. Freeze is a fix-only state, not “never change any code again.”

### C. Release approval / production-write gate

Freezing source does not publish packages, finish a pilot or approve production writes.

- Independent security/transaction review is required before sensitive production/pilot writes. Findings affecting correctness or access block that approval and enter the fix-only process.
- Maven Central namespace/signing/publishing access, hosting ownership and final artifact/download checks must exist before the corresponding publication claim.
- Independent installation, measured pilot usage and commercial feedback are external evidence, not CI outputs. Recruit/send messages only with the owner's explicit authorization.
- Missing reviewers, credentials or pilot participants leave the candidate frozen while those activities proceed. They must not trigger indefinite feature additions or fabricated completion claims.
- An early developer preview must describe unsupported host/external effects accurately. A production decision is specific to the integration and cannot be inferred from a release tag.

## 5. What can change after code freeze

| Change | Rule |
|---|---|
| Security, data-loss, duplicate-effect, migration or incorrect-outcome fix | Allowed and required; reproduce, fix, retest and issue a new candidate SHA |
| Core-workflow accessibility/responsiveness fix | Allowed; verify affected keyboard/device/screen-reader paths and regression checks |
| Release-blocking compatibility/install fix | Allowed; rerun consumer and relevant database/package checks |
| Documentation correcting a factual error or release instructions | Allowed; must match the candidate and not imply new guarantees |
| Dependency update | Only for an identified security/release blocker; record compatibility and asset/license implications |
| Cosmetic preference, speculative refactor, new integration or feature | Deferred; no candidate change |

For every fix, record the defect, impact, affected contract, evidence before/after and new SHA. Rerun full CI for code changes; rerun database/migration/browser/consumer checks where affected. Any changed deployed artifact invalidates its previous exact-build verification for affected paths. Tag/approve only the final verified candidate; do not move an existing release tag to different code.

## 6. Reopening scope

Only the product owner can reopen feature scope. Record the requested change, user problem, evidence, affected guarantees, migration/testing cost and whether it replaces existing scope or starts a later cycle. Do not quietly change the freeze target or remove an agreed requirement.

Implementation details can be resolved by engineering within an existing package. Permission is not needed for every reversible fix or routine test. A review suggesting a different colour or another feature is not itself a scope decision.

## 7. Freeze record — fill with evidence, not assumptions

```text
Candidate/version:
Source SHA:
GitHub master/ref and SHA:
GitLab Render-source ref and SHA:
Artifact/checksum and build environment:
Actual deployed SHA / environment / verification date:
BE-01–BE-08 acceptance evidence:
UI-01–UI-06 acceptance evidence:
Java / JavaScript CI results:
PostgreSQL version + migration / concurrency / restart evidence:
Clean consumer install / upgrade evidence:
Hosted device / keyboard / screen-reader / contrast results:
Known minor issues and accepted exceptions:
Independent review status (separate release gate):
Publishing / pilot prerequisites (separate release gates):
Code-freeze decision, owner and date:
Publication / production-write decision (separate, if any):
```

Current state (9 October): scope defined; code-freeze criteria are not yet met. Restricted relationship labels, managed local actions/proposals, bounded export and an independent restart fixture are implemented in the working candidate. Final upgraded-build verification, PostgreSQL migration/restart CI, exact hosted accessibility evidence and the remaining acceptance checks still prevent declaring code freeze. See the completion ledger for current evidence rather than treating the original review findings as unfixed.
