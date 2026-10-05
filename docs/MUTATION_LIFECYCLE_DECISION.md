# Mutation lifecycle decision

Date: 5 October 2026. Status: design accepted for staged implementation; transaction guarantees below are not implemented yet.

Implementation progress: legacy action orchestration has been extracted into `ActionMutationService`. Its direct entry point obtains the actor through `MutationActorProvider` and checks console, entity and action access before loading or executing a record. The default Spring Security adapter rejects missing and anonymous authentication. Hosts using custom authentication must provide a trusted server-context actor provider; absent that provider, direct mutation access fails closed. The controller retains redirects and validation feedback. CRUD extraction, structured outcome expansion, managed transactions and atomic auditing remain pending.

Compatibility: annotated/programmatic action registrations and HTTP routes are unchanged. Hosts manually constructing `AdminController` must now supply the action service dependency. The existing optional-version behavior is retained for this extraction; mandatory version enforcement is a later change with its own migration and concurrency tests. Missing execution handlers now reject with 422 instead of reporting an operation as successful.

## Observed baseline

`AdminController` coordinates create, update, delete and actions. Query-engine writes each have their own transactional method. After those calls return, the controller publishes `VectisChangeEvent`. `VectisAuditLogService` handles it in `REQUIRES_NEW` and catches persistence errors. A committed change can therefore lack a success audit. Wrapping the controller in a transaction alone would not repair this: the independent audit transaction and swallowed failures would remain.

Action handlers may invoke host services or external systems. A handler exception does not prove that nothing happened. Until structured outcomes exist, unexpected action failures must ask the operator to check the record and connected systems, not blindly retry. A failure-injection regression exercises a test double that records an effect and then throws.

## Execution modes

1. **Managed local JPA:** one transaction manager owns the entity write, success audit and eventual idempotency result. A failure in any of these rolls back all three. Database flush and commit failures must reach the caller before success is returned.
2. **Host service or external effect:** explicit host-owned integration contract. Independent transactions, HTTP calls, email and jobs do not inherit local rollback guarantees. Preserve legacy registrations through an adapter labelled as having host-owned effects; do not silently classify existing handlers as managed.

Do not infer a mode from an annotation's absence or claim that arbitrary Java handlers can be sandboxed. New managed registrations explicitly promise local-only effects. Host outbox integrations distinguish queued and delivered results.

## Service boundary

Introduce a mutation application service for create/update/delete/actions. Controllers retain HTTP binding, form feedback, HTMX handling and redirects. The service must also enforce authorization when invoked without a controller.

Request data includes operation kind, allowlisted entity/action identity, record identifier, bounded input, reason, expected version, proposal reference and idempotency key. Actor identity comes from authenticated server context through a host adapter; a request field cannot supply it. Resolve descriptors/actions server-side. Reject unknown identifiers and unsupported execution modes before invoking a handler.

Execution order: authenticate → authorize console and operation → validate input → load permitted record → verify expected version/proposal → check eligibility → mutate → validate → flush → project permitted persisted state → persist success audit and result → commit → return. Create has no pre-existing version; update/delete/actions on guarded versioned records require one. Relationship permissions remain enforced during binding.

The transactional executor returns only after commit. Do not catch persistence failures inside it and return success. Map domain exceptions to structured outcomes outside the transaction: rejected, invalid, conflict, failed, succeeded or unknown/pending for host effects. Keep submitted input/reason for recoverable form failures without exposing hidden fields.

## Audit and replay

Use an explicit audit writer for managed operations in the same transaction manager. Retire the independent success-event listener for those paths to prevent duplicate records. Keep public events only under a documented notification contract; subscriber behavior cannot secretly redefine whether a committed operation succeeded.

Before/after projections omit ignored and unauthorized fields. Include operation ID, authenticated actor, timestamp, entity/action identity and bounded reason. A separately stored failed attempt is not a success audit and must not leak forbidden snapshots.

Durable idempotency uses a database uniqueness constraint scoped to actor and operation identity plus a canonical payload fingerprint. Replays require fresh authorization. Changed payload with the same key is rejected. Unknown external results are never automatically executed again. Retention must exceed the supported retry window.

## Implementation sequence and proof

| Slice | Required evidence |
|---|---|
| Extract request/result and authorization boundary | Direct-service denial tests; current route/custom-path/form regressions remain green |
| Managed CRUD plus success audit | Constraint failure leaves no success audit; injected audit failure rolls back entity; commit/flush failure never returns success |
| Explicit action modes | Managed local action shares transaction; legacy/host effects carry no atomicity claim; failure after dispatch reports unknown outcome |
| Version enforcement | Missing/malformed versions reject; two independent transactions produce one winner and one conflict; stale delete and no-field-change actions covered |
| Durable replay protection | Concurrent duplicates, lost response, restart, changed fingerprint and unauthorized replay covered with multiple connections |
| Proposal binding | Actor/action/record/input/version/expiry checked again at execution; revoked permission and stale proposal rejected |

Run failure/concurrency cases against PostgreSQL as well as H2 before claiming these guarantees. Tests must not wrap HTTP requests in a test-owned transaction when testing commit behavior. Independent review remains required before pilot production writes. This decision does not complete stages B–E.
