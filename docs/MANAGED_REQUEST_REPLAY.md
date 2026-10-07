# Managed CRUD request replay

Create/edit/delete require a UUID `_operation` key. Forms supply one; the delete dialog generates one per opening. Retrying the same attempt must reuse its key, version, reason and input. A new deliberate operation gets a new key. Failed validation rolls the receipt back, so correcting that form can reuse its key.

`vectis_mutation_receipts` stores a hash of actor identity plus key, a canonical request fingerprint, completion state, result identifier and creation time. No submitted field values or CSRF tokens are stored in this table. Fingerprints include operation/entity/record identity and input, including version and reason; `_csrf`, `_list` and `_operation` are excluded. Request limits are 100 parameters, 200-character names, 65,536 characters per value and 131,072 total value characters.

The reservation, entity mutation, success audit and completion receipt share the default JPA transaction. A rollback leaves none committed. A matching completed request returns its recorded identifier without executing again. Changed input using the same key returns 409. Current console/entity/write and submitted-relationship permissions are checked before a receipt is returned.

Concurrent requests contend on the database primary key. One transaction can reserve and execute. A competing request can receive the committed result or a 409 reservation conflict; retrying the exact request after completion returns the stored result. A reservation database failure also returns 409 with no success claim. It is not safe to replace the key automatically after an uncertain network response.

This protects managed CRUD, not custom actions or arbitrary external callbacks. It does not promise exactly-once external execution. Receipt storage is database-backed rather than process-local; actual process-restart testing remains a release gate until recorded separately.

## Schema and retention

Production hosts must provision the receipt table in the same database/transaction manager as records and audit. An explicit PostgreSQL schema example is in `sql/mutation-receipts-postgresql.sql`; it is not automatically applied. The sample's disposable database recreates its schema on startup, which also discards receipts and data. That reset behavior is not production durability.

There is deliberately no automatic receipt expiration or cleanup in this slice. Retain receipts for the entire promised retry horizon; keeping them indefinitely preserves replay protection while the data store survives. Before introducing cleanup, define and enforce a supported retry expiry at the API boundary. Deleting a receipt without such enforcement permits that old key to execute again. Backups/restores must treat records, audit and receipts as one consistent data set.

Manual construction of `RecordMutationService` now requires `MutationReceiptStore`. Hosts explicitly listing entity packages must include `io.github.yavonalabs.vectis.core.mutation`. The starter's default entity scan includes it. Legacy action contracts, reviewed-proposal binding and conflict input recovery remain separate work.
