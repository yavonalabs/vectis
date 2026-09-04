package io.github.yavonalabs.vectis.core.query;

import io.github.yavonalabs.vectis.core.metadata.AssociationDescriptor;
import io.github.yavonalabs.vectis.core.metadata.EntityDescriptor;
import io.github.yavonalabs.vectis.core.metadata.FieldDescriptor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DynamicCriteriaQueryEngine {

    private final EntityManager entityManager;
    private final Validator validator;

    public DynamicCriteriaQueryEngine(EntityManager entityManager, Validator validator) {
        this.entityManager = entityManager;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public <T> PageResult<T> findPage(
            EntityDescriptor descriptor,
            int page,
            int size,
            String search,
            String sortProperty,
            String sortDirection
    ) {
        @SuppressWarnings("unchecked")
        Class<T> javaType = (Class<T>) descriptor.javaType();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> countRoot = countQuery.from(javaType);
        countQuery.select(cb.count(countRoot));

        Predicate countPredicate = buildSearchPredicate(cb, countRoot, descriptor, search);
        if (countPredicate != null) {
            countQuery.where(countPredicate);
        }
        long totalElements = entityManager.createQuery(countQuery).getSingleResult();

        CriteriaQuery<T> query = cb.createQuery(javaType);
        Root<T> root = query.from(javaType);

        // Fetch join single-valued associations (@ManyToOne, @OneToOne) for OSIV-safe display
        for (AssociationDescriptor assoc : descriptor.associations()) {
            if (assoc.isSingleValued()) {
                root.fetch(assoc.name(), JoinType.LEFT);
            }
        }

        query.select(root).distinct(true);

        Predicate searchPredicate = buildSearchPredicate(cb, root, descriptor, search);
        if (searchPredicate != null) {
            query.where(searchPredicate);
        }

        Set<String> validFields = descriptor.fields().stream()
                .map(FieldDescriptor::name)
                .collect(Collectors.toSet());

        if (sortProperty != null && validFields.contains(sortProperty)) {
            Path<?> sortPath = root.get(sortProperty);
            if ("desc".equalsIgnoreCase(sortDirection)) {
                query.orderBy(cb.desc(sortPath));
            } else {
                query.orderBy(cb.asc(sortPath));
            }
        } else if (descriptor.idField() != null) {
            query.orderBy(cb.asc(root.get(descriptor.idField().name())));
        }

        TypedQuery<T> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(page * size);
        typedQuery.setMaxResults(size);

        List<T> content = typedQuery.getResultList();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return new PageResult<>(content, page, size, totalElements, totalPages);
    }

    @Transactional(readOnly = true)
    public <T> T findById(EntityDescriptor descriptor, Object id) {
        @SuppressWarnings("unchecked")
        Class<T> javaType = (Class<T>) descriptor.javaType();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(javaType);
        Root<T> root = query.from(javaType);

        // Scope fetch-joins exclusively to single-valued associations to prevent MultipleBagFetchException
        for (AssociationDescriptor assoc : descriptor.associations()) {
            if (assoc.isSingleValued()) {
                root.fetch(assoc.name(), JoinType.LEFT);
            }
        }

        if (descriptor.idField() != null) {
            query.where(cb.equal(root.get(descriptor.idField().name()), id));
        }

        query.select(root).distinct(true);

        try {
            T result = entityManager.createQuery(query).getSingleResult();
            if (result != null) {
                // Safely initialize collection associations within the transaction boundary
                BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(result);
                for (AssociationDescriptor assoc : descriptor.associations()) {
                    if (!assoc.isSingleValued()) {
                        Object col = wrapper.getPropertyValue(assoc.name());
                        if (col instanceof Collection<?> c) {
                            c.size(); // Triggers collection load safely
                        }
                    }
                }
            }
            return result;
        } catch (NoResultException e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public <T> List<T> findAll(EntityDescriptor descriptor) {
        @SuppressWarnings("unchecked")
        Class<T> javaType = (Class<T>) descriptor.javaType();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(javaType);
        Root<T> root = query.from(javaType);

        if (descriptor.idField() != null) {
            query.orderBy(cb.asc(root.get(descriptor.idField().name())));
        }

        return entityManager.createQuery(query).getResultList();
    }

    @Transactional
    public <T> T save(T entity) {
        Set<ConstraintViolation<T>> violations = validator.validate(entity);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return entityManager.merge(entity);
    }

    @Transactional
    public void persist(Object entity) {
        if (validator != null) {
            Set<ConstraintViolation<Object>> violations = validator.validate(entity);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
        }
        entityManager.persist(entity);
    }

    @Transactional
    public void deleteById(EntityDescriptor descriptor, Object id) {
        Object entity = findById(descriptor, id);
        if (entity != null) {
            entityManager.remove(entity);
        }
    }

    private <T> Predicate buildSearchPredicate(
            CriteriaBuilder cb,
            Root<T> root,
            EntityDescriptor descriptor,
            String search
    ) {
        if (search == null || search.isBlank()) {
            return null;
        }

        String pattern = "%" + search.toLowerCase().trim() + "%";
        List<Predicate> predicates = new ArrayList<>();

        for (FieldDescriptor field : descriptor.fields()) {
            if (field.isString()) {
                predicates.add(cb.like(cb.lower(root.get(field.name())), pattern));
            }
        }

        if (predicates.isEmpty()) {
            return null;
        }

        return cb.or(predicates.toArray(new Predicate[0]));
    }
}
