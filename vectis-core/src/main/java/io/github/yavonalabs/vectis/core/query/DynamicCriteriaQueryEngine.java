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
        return findPage(descriptor, page, size, search, sortProperty, sortDirection, List.of());
    }

    @Transactional(readOnly = true)
    public <T> PageResult<T> findPage(EntityDescriptor descriptor, int page, int size,
            String search, String sortProperty, String sortDirection, List<RecordFilter> filters) {
        @SuppressWarnings("unchecked")
        Class<T> javaType = (Class<T>) descriptor.javaType();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Page must be nonnegative and page size must be between 1 and 100.");
        }
        if (sortProperty != null && !sortProperty.isBlank() && descriptor.fields().stream().noneMatch(f -> f.name().equals(sortProperty))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unknown sort field.");
        }
        if (sortDirection != null && !sortDirection.equalsIgnoreCase("asc") && !sortDirection.equalsIgnoreCase("desc")) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Sort direction must be asc or desc.");
        }
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<T> countRoot = countQuery.from(javaType);
        countQuery.select(cb.count(countRoot));

        Predicate countPredicate = buildPredicate(cb, countRoot, descriptor, search, filters);
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

        Predicate searchPredicate = buildPredicate(cb, root, descriptor, search, filters);
        if (searchPredicate != null) {
            query.where(searchPredicate);
        }

        Set<String> validFields = descriptor.fields().stream()
                .map(FieldDescriptor::name)
                .collect(Collectors.toSet());

        if (sortProperty != null && validFields.contains(sortProperty)) {
            Path<?> sortPath = root.get(sortProperty);
            if ("desc".equalsIgnoreCase(sortDirection)) {
                query.orderBy(cb.desc(sortPath), cb.asc(root.get(descriptor.idField().name())));
            } else {
                query.orderBy(cb.asc(sortPath), cb.asc(root.get(descriptor.idField().name())));
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
        return findById(descriptor, id, true);
    }

    /** Scalar projection only: never materializes related entities or hidden fields. */
    @Transactional(readOnly = true)
    public List<jakarta.persistence.Tuple> exportRows(EntityDescriptor descriptor, List<FieldDescriptor> columns,
            String search, String sort, String direction, List<RecordFilter> filters) {
        if (columns.isEmpty() || columns.size() > 20 || columns.stream().anyMatch(f -> !descriptor.fields().contains(f) || f.isEmbeddedId() || f.isVersion()))
            throw new IllegalArgumentException("Invalid export projection");
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        var root = query.from(descriptor.javaType());
        List<Selection<?>> selection = new ArrayList<>();
        for (var column : columns) {
            // Bound text before JDBC materializes it. The service rejects oversized cells.
            selection.add(column.isString() ? cb.substring(root.<String>get(column.name()), 1, 2049) : root.get(column.name()));
        }
        query.multiselect(selection);
        Predicate predicate = buildPredicate(cb, root, descriptor, search, filters);
        if (predicate != null) query.where(predicate);
        String order = sort == null || sort.isBlank() ? descriptor.idField().name() : sort;
        if (descriptor.fields().stream().noneMatch(f -> f.name().equals(order))) throw new IllegalArgumentException("Invalid sort");
        query.orderBy("desc".equals(direction) ? cb.desc(root.get(order)) : cb.asc(root.get(order)), cb.asc(root.get(descriptor.idField().name())));
        return entityManager.createQuery(query).setMaxResults(1001).setHint("jakarta.persistence.query.timeout", 10000).getResultList();
    }

    @Transactional(readOnly = true)
    public <T> T findById(EntityDescriptor descriptor, Object id, boolean initializeCollections) {
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
            if (result != null && initializeCollections) {
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

    /** Pages the association join itself, without initializing the source collection. */
    @Transactional(readOnly = true)
    public PageResult<Map<String, String>> findRelatedPage(EntityDescriptor source, Object id,
            AssociationDescriptor association, EntityDescriptor target, int page) {
        final int size = 25;
        if (page < 0 || (long) page * size > Integer.MAX_VALUE)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid related-record page.");
        if (association.isSingleValued() || !source.associations().contains(association)
                || !association.targetEntityClass().equals(target.javaType()))
            throw new IllegalArgumentException("Invalid collection association");
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<?> countSource = count.from(source.javaType());
        Join<?, ?> countTarget = countSource.join(association.name());
        count.select(cb.countDistinct(countTarget.get(target.idField().name())))
                .where(cb.equal(countSource.get(source.idField().name()), id));
        long total = entityManager.createQuery(count).getSingleResult();
        CriteriaQuery<Object> query = cb.createQuery(Object.class);
        Root<?> root = query.from(source.javaType());
        Join<?, ?> related = root.join(association.name());
        query.select(related).distinct(true).where(cb.equal(root.get(source.idField().name()), id))
                .orderBy(cb.asc(related.get(target.idField().name())));
        List<Map<String, String>> items = entityManager.createQuery(query).setFirstResult(page * size)
                .setMaxResults(size).getResultList().stream().map(item -> {
                    Object targetId = PropertyAccessorFactory.forBeanPropertyAccess(item).getPropertyValue(target.idField().name());
                    return Map.of("display", io.github.yavonalabs.vectis.core.metadata.RecordPresentation.label(target, item),
                            "slug", target.slug(), "encodedId", io.github.yavonalabs.vectis.core.routing.IdCodec.encode(targetId, target.idField().isEmbeddedId()));
                }).toList();
        return new PageResult<>(items, page, size, total, (int) Math.ceil((double) total / size));
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

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void flush() {
        entityManager.flush();
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

        String pattern = "%" + escapeLike(search.toLowerCase(Locale.ROOT).trim()) + "%";
        List<Predicate> predicates = new ArrayList<>();

        for (FieldDescriptor field : descriptor.fields()) {
            if (field.isString()) {
                predicates.add(cb.like(cb.lower(root.get(field.name())), pattern, '\\'));
            } else if (field.isEnum()) {
                predicates.add(cb.like(cb.lower(root.get(field.name()).as(String.class)), pattern, '\\'));
            }
        }

        if (predicates.isEmpty()) {
            return cb.disjunction();
        }

        return cb.or(predicates.toArray(new Predicate[0]));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> Predicate buildPredicate(CriteriaBuilder cb, Root<T> root, EntityDescriptor descriptor,
            String search, List<RecordFilter> filters) {
        List<Predicate> conditions = new ArrayList<>();
        Predicate text = buildSearchPredicate(cb, root, descriptor, search);
        if (text != null) conditions.add(text);
        for (RecordFilter filter : filters) {
            Path path = root.get(filter.field());
            Object value = filter.value();
            conditions.add(switch (filter.operator()) {
                case "eq" -> cb.equal(path, value);
                case "ne" -> cb.notEqual(path, value);
                case "contains" -> cb.like(cb.lower(path), "%" + escapeLike(value.toString().toLowerCase(Locale.ROOT)) + "%", '\\');
                case "gt" -> cb.greaterThan(path, (Comparable) value);
                case "gte" -> cb.greaterThanOrEqualTo(path, (Comparable) value);
                case "lt" -> cb.lessThan(path, (Comparable) value);
                case "lte" -> cb.lessThanOrEqualTo(path, (Comparable) value);
                case "empty" -> cb.isNull(path);
                case "notEmpty" -> cb.isNotNull(path);
                default -> throw new IllegalArgumentException("Unsupported filter operator");
            });
        }
        return conditions.isEmpty() ? null : cb.and(conditions.toArray(new Predicate[0]));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
