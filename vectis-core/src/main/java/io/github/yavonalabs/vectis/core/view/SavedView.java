package io.github.yavonalabs.vectis.core.view;

import jakarta.persistence.*;

@Entity
@Table(name = "vectis_saved_views", uniqueConstraints = @UniqueConstraint(name = "uq_vectis_view_slot", columnNames = {"owner", "entity_slug", "view_slot"}))
public class SavedView {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 200) private String owner;
    @Column(name = "entity_slug", nullable = false, length = 200) private String entitySlug;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false) private int schemaVersion;
    @Column(name = "view_slot", nullable = false) private int slot;
    @Column(name = "query_state", nullable = false, length = 8192) private String queryState;
    protected SavedView() {}
    public SavedView(String owner, String slug, String name, String query, int slot) {
        this.id = java.util.UUID.randomUUID().toString(); this.owner = owner;
        this.entitySlug = slug; this.name = name; this.queryState = query; this.schemaVersion = 1;
        this.slot = slot;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getQueryState() { return queryState; }
    public int getSchemaVersion() { return schemaVersion; }
}
