-- Apply with your host application's schema migration mechanism before upgrading.
-- Do not apply to a database where Hibernate has already created this table.
CREATE TABLE vectis_saved_views (
    id varchar(36) PRIMARY KEY,
    owner varchar(200) NOT NULL,
    entity_slug varchar(200) NOT NULL,
    name varchar(80) NOT NULL,
    schema_version integer NOT NULL,
    view_slot integer NOT NULL CHECK (view_slot >= 0 AND view_slot < 50),
    query_state varchar(8192) NOT NULL,
    CONSTRAINT uq_vectis_view_slot UNIQUE (owner, entity_slug, view_slot)
);
