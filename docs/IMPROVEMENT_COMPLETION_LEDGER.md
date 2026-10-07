# Improvement completion ledger

Updated 7 October 2026. All stages in NEXT_IMPLEMENTATION_PLAN.md remain required. A deployed build is evidence for that commit, not completion of this roadmap.

| Area | Implemented / verified | Remaining gate or implementation |
|---|---|---|
| Hosted demo | Three roles, HTTP permission checks; disposable CRUD/audit journey | Full device/keyboard/contrast/screen-reader matrix; exact build identity; hosted verification of subsequent changes |
| Distribution page/docs | Public repository, branded draft product page, integration/security documentation | Publish verified product page; reconcile all release claims and installation walkthrough |
| Mutation services | Server-derived actors; direct-service authorization; CRUD/action extraction | Complete typed outcomes and input-preserving conflict journey |
| Managed CRUD atomicity | Same-transaction audit; rollback/failure tests; prior PostgreSQL CI pass reported by owner | Independent review; each later PostgreSQL run must pass separately |
| Version checks | Required versioned action/edit/delete input; concurrent managed-edit test | Managed custom-action transaction modes; no-field-change and delete-race coverage |
| Duplicate requests | Managed CRUD receipts and form keys in current implementation | Final replay tests/CI, actual restart evidence, explicit retry-expiry/cleanup policy; custom actions excluded |
| Preview/policy | Explicit preview handlers; independently testable restricted role | Reviewed proposal bound to actor/input/version/expiry and enforced at execution |
| Packaging | Source build, CI, Docker demo | Separate consuming app, supported version/database matrix, bundled UI dependencies, tagged prerelease, Maven Central prerequisites |
| Unsaved edits | In-memory dirty guard, HTMX/session handling, in-flight edit preservation; automated tests and local successful-save/expired-session browser checks | Hosted verification, native cancel-navigation and mobile browser coverage |
| Saved views | Owner-scoped create/open/remove, versioned allowlisted state, permission rechecks, bounded storage; local browser workflow and H2 integration checks | Current PostgreSQL CI and hosted verification, final mobile check and restart persistence evidence |
| Export | Required scope | Dedicated permission, safe projection, bounded rows/memory, formula neutralization |
| External pilots | Plan and decision criteria | Recruit participating teams with owner authorization; independent installation, two-week repeated-use evidence, commercial feedback |

External pilot evidence, independent security/transaction review, account ownership and publishing credentials cannot be manufactured by code changes. Mark them complete only when the evidence exists. Continue available engineering work while those gates remain open.
