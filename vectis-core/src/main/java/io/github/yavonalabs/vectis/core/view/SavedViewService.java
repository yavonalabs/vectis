package io.github.yavonalabs.vectis.core.view;

import io.github.yavonalabs.vectis.core.metadata.*;
import io.github.yavonalabs.vectis.core.mutation.MutationActorProvider;
import io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Transactional(readOnly = true)
public class SavedViewService {
    private final EntityManager em;
    private final EntityMetadataRegistry metadata;
    private final AdminPermissionEvaluator permissions;
    private final MutationActorProvider actors;
    public SavedViewService(EntityManager em, EntityMetadataRegistry metadata, AdminPermissionEvaluator permissions, MutationActorProvider actors) {
        this.em = em; this.metadata = metadata; this.permissions = permissions; this.actors = actors;
    }
    private String owner(String slug) {
        var actor = actors.currentActor();
        if (actor == null || actor.getName() == null || actor.getName().isBlank() || actor.getName().length() > 200
                || !permissions.canAccessAdmin(actor) || !permissions.canViewEntity(slug, actor))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to saved views.");
        descriptor(slug);
        return actor.getName();
    }
    private EntityDescriptor descriptor(String slug) {
        return metadata.getBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public List<SavedView> list(String slug) {
        return em.createQuery("select v from SavedView v where v.owner=:owner and v.entitySlug=:slug order by v.name, v.id", SavedView.class)
                .setParameter("owner", owner(slug)).setParameter("slug", slug).setMaxResults(50).getResultList();
    }
    @Transactional
    public String create(String slug, String name, String state) {
        String owner = owner(slug);
        if (name == null || name.isBlank() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name your view using 1 to 80 characters.");
        String validated = SavedViewState.validate(descriptor(slug), state);
        var slots = em.createQuery("select v.slot from SavedView v where v.owner=:owner and v.entitySlug=:slug", Integer.class)
                .setParameter("owner", owner).setParameter("slug", slug).getResultList();
        int slot = java.util.stream.IntStream.range(0, 50).filter(i -> !slots.contains(i)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Remove a saved view before adding another (limit 50 per section)."));
        SavedView view = new SavedView(owner, slug, name.trim(), validated, slot);
        try { em.persist(view); em.flush(); }
        catch (jakarta.persistence.PersistenceException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The view could not be saved. Refresh your views before trying again.");
        }
        return view.getId();
    }
    public String open(String slug, String id) {
        SavedView view = owned(slug, id);
        if (view.getSchemaVersion() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "This saved view version is not supported. Rebuild it from the record list.");
        return SavedViewState.validate(descriptor(slug), view.getQueryState());
    }
    @Transactional
    public void delete(String slug, String id) { em.remove(owned(slug, id)); }
    private SavedView owned(String slug, String id) {
        String owner = owner(slug);
        if (id == null || id.length() != 36) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return em.createQuery("select v from SavedView v where v.id=:id and v.owner=:owner and v.entitySlug=:slug", SavedView.class)
                .setParameter("id", id).setParameter("owner", owner).setParameter("slug", slug)
                .getResultStream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
