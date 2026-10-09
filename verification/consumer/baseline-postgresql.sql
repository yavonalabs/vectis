-- Disposable consumer schema representing the pre-upgrade audit installation.
-- The normal Vectis upgrade SQL is then applied by CI, followed by Hibernate validate.
CREATE TABLE consumer_record (id bigint PRIMARY KEY, version bigint, name varchar(255), completed integer);
CREATE TABLE consumer_team (id bigint PRIMARY KEY, name varchar(255));
ALTER TABLE consumer_record ADD COLUMN team_id bigint REFERENCES consumer_team(id);
CREATE TABLE vectis_audit_logs (
    id varchar(36) PRIMARY KEY, entity_slug varchar(100) NOT NULL,
    encoded_entity_id varchar(255) NOT NULL, action_name varchar(150) NOT NULL,
    operation varchar(20) NOT NULL, actor_username varchar(100), reason varchar(1000),
    before_snapshot_json oid, after_snapshot_json oid, timestamp timestamp(6) with time zone NOT NULL
);
CREATE INDEX idx_vectis_audit_entity ON vectis_audit_logs(entity_slug, encoded_entity_id);
CREATE INDEX idx_vectis_audit_ts ON vectis_audit_logs(timestamp);
