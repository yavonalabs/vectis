package io.github.yavonalabs.vectis.core.web;

import io.github.yavonalabs.vectis.core.metadata.EntityDescriptor;
import io.github.yavonalabs.vectis.core.metadata.EntityMetadataRegistry;
import io.github.yavonalabs.vectis.core.metadata.FieldDescriptor;
import io.github.yavonalabs.vectis.core.routing.IdCodec;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${vectis.path:/admin}/api/search")
public class GlobalSearchController {

    private final EntityMetadataRegistry registry;
    private final EntityManager entityManager;
    private final io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator permissions;
    @org.springframework.beans.factory.annotation.Value("${vectis.path:/admin}") private String adminPath;

    public GlobalSearchController(EntityMetadataRegistry registry, EntityManager entityManager, io.github.yavonalabs.vectis.core.security.AdminPermissionEvaluator permissions) {
        this.permissions = permissions;
        this.registry = registry;
        this.entityManager = entityManager;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> search(@RequestParam("q") String query, java.security.Principal principal, jakarta.servlet.http.HttpServletRequest request) {
        if (!permissions.canAccessAdmin(principal)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        if (query != null && query.length() > 200) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Search is too long.");
        List<Map<String, Object>> results = new ArrayList<>();
        if (query == null || query.trim().length() < 2) {
            return results;
        }

        String searchTerm = "%" + query.trim().toLowerCase() + "%";

        for (EntityDescriptor descriptor : registry.getAllDescriptors()) {
            if (!permissions.canViewEntity(descriptor.slug(), principal)) continue;
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Object> cq = (CriteriaQuery<Object>) cb.createQuery(descriptor.javaType());
            Root<Object> root = (Root<Object>) cq.from(descriptor.javaType());

            List<Predicate> predicates = new ArrayList<>();
            for (FieldDescriptor fd : descriptor.fields()) {
                if (fd.type().equals(String.class)) {
                    predicates.add(cb.like(cb.lower(root.get(fd.name())), searchTerm));
                }
            }

            if (!predicates.isEmpty()) {
                cq.where(cb.or(predicates.toArray(new Predicate[0])));
                List<Object> entityResults = entityManager.createQuery(cq).setMaxResults(5).getResultList();
                
                if (!entityResults.isEmpty()) {
                    List<Map<String, String>> items = new ArrayList<>();
                    for (Object entity : entityResults) {
                        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
                        Object rawId = wrapper.getPropertyValue(descriptor.idField().name());
                        String encodedId = IdCodec.encode(rawId, descriptor.idField().isEmbeddedId());
                        
                        items.add(Map.of(
                                "title", io.github.yavonalabs.vectis.core.metadata.RecordPresentation.label(descriptor, entity),
                                "url", request.getContextPath() + adminPath + "/" + descriptor.slug() + "/view/" + encodedId
                        ));
                    }
                    results.add(Map.of(
                            "category", descriptor.displayName(),
                            "slug", descriptor.slug(),
                            "items", items
                    ));
                }
            }
        }
        return results;
    }
}
