-- Upgrade existing installations before deploying this candidate.
ALTER TABLE vectis_mutation_receipts ADD COLUMN entity_slug varchar(200);
ALTER TABLE vectis_mutation_receipts ADD COLUMN operation varchar(200);
ALTER TABLE vectis_audit_logs ADD COLUMN operation_id varchar(36);
-- Old receipts retain replay protection. Their metadata is not inferred/backfilled:
-- use the original exact request for replay, not the new result-inspection endpoint.
