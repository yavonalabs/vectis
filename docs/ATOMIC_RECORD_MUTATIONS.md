# Managed record mutations and audit

Create, edit and delete through `RecordMutationService` now run inside one Spring transaction. Record writes are flushed before the final save snapshot; the success audit is inserted and flushed through `VectisAuditLogService.recordMutation`, which requires an existing transaction and propagates errors. The controller reports success only after the service invocation returns.

This contract requires the default JPA transaction manager to own both the entity and audit tables. Multiple databases/transaction managers and custom query engines or audit writers are outside this verified configuration. If a host calls the service inside an existing transaction, the host owns the final commit; a returned service result is provisional until that outer transaction commits. Database callbacks that invoke external systems cannot be rolled back by JPA.

## Compatibility

- Generic CRUD calls the audit writer directly instead of publishing `VectisChangeEvent`. This avoids the independent event listener producing duplicate or detached success records. Hosts subscribing to CRUD change events must account for this change; no replacement after-commit notification API is promised in this slice.
- Legacy custom actions retain their event-based audit and independent transaction behavior. They do not inherit this managed CRUD contract.
- Hosts manually constructing `RecordMutationService` now supply `VectisAuditLogService` instead of an event publisher.
- Supplied edit versions are compared with the loaded version rather than overwriting a managed entity's version. Mandatory version submission, explicit delete version checks, reviewed proposals and retry deduplication remain separate work.
- Validation feedback still returns submitted form values. Audit storage failures are failures, not successful changes with missing history.

## Verification

`AtomicRecordMutationTest` does not run inside a test-owned transaction. It reads back state after the service commits or rolls back. It covers successful create/update/delete audits, failure after the audit has been flushed, an actual audit-column constraint failure, and an entity uniqueness failure. Tests use isolated disposable fixtures.

The default test database is H2. CI adds a PostgreSQL 16 service and runs the same class using `VECTIS_TEST_DB_URL`, `VECTIS_TEST_DB_DRIVER`, `VECTIS_TEST_DB_USER` and `VECTIS_TEST_DB_PASSWORD`. Only point these variables at a disposable test database: the sample recreates its schema. A configured CI job is not evidence of a passing PostgreSQL run; record its actual result before declaring the database verification gate complete.

This is a managed CRUD implementation slice, not completion of mutation reliability or approval for production writes. Independent review, PostgreSQL evidence, guarded action modes, mandatory concurrency checks and durable idempotency are still required.
