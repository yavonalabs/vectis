-- Apply once before enabling managed actions requiring confirmation.
-- Receipt schema is also required; use the same database/transaction manager as host entities.
CREATE TABLE vectis_action_proposals (
    id varchar(36) PRIMARY KEY,
    fingerprint varchar(64) NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    consumed boolean NOT NULL
);
-- Expired proposals may be deleted: absence is rejected, never treated as authorization.
-- Completed mutation receipts must NOT be automatically deleted.
