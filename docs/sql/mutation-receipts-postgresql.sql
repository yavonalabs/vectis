-- Apply using the host application's migration system to the same schema as the records and audit.
-- Never use the disposable sample's create-drop configuration against production data.
CREATE TABLE vectis_mutation_receipts (
    id VARCHAR(64) PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    result_id VARCHAR(255),
    completed BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
