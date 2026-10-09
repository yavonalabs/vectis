package io.github.yavonalabs.vectis.core.metadata;

import io.github.yavonalabs.vectis.core.annotation.AdminEntity;
import io.github.yavonalabs.vectis.core.annotation.AdminIgnore;
import io.github.yavonalabs.vectis.core.annotation.AdminField;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Version;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EntityMetadataRegistry {

    private final Map<String, EntityDescriptor> descriptorsBySlug = new ConcurrentHashMap<>();
    private final Map<Class<?>, EntityDescriptor> descriptorsByClass = new ConcurrentHashMap<>();
    private final Set<String> allowedClassNames;

    public EntityMetadataRegistry(EntityManagerFactory emf) {
        this(emf, Collections.emptySet());
    }

    public EntityMetadataRegistry(EntityManagerFactory emf, Set<String> allowedClassNames) {
        this.allowedClassNames = allowedClassNames != null ? allowedClassNames : Collections.emptySet();
        scanMetamodel(emf);
    }

    private void scanMetamodel(EntityManagerFactory emf) {
        Set<EntityType<?>> entities = emf.getMetamodel().getEntities();
        for (EntityType<?> entityType : entities) {
            Class<?> javaType = entityType.getJavaType();
            if (javaType == null) continue;

            // STRICT OPT-IN: Only register if annotated with @AdminEntity OR listed in allowedClassNames
            boolean isOptedIn = javaType.isAnnotationPresent(AdminEntity.class) ||
                                allowedClassNames.contains(javaType.getName()) ||
                                allowedClassNames.contains(javaType.getSimpleName());

            if (!isOptedIn) {
                continue;
            }

            AdminEntity adminAnnotation = javaType.getAnnotation(AdminEntity.class);
            String customLabel = adminAnnotation != null && !adminAnnotation.label().isBlank() ? adminAnnotation.label() : null;

            String slug = toSlug(entityType.getName());
            EntityDescriptor descriptor = buildDescriptor(entityType, slug, customLabel);
            descriptorsBySlug.put(slug, descriptor);
            descriptorsByClass.put(javaType, descriptor);
        }
    }

    private EntityDescriptor buildDescriptor(EntityType<?> entityType, String slug, String customLabel) {
        Class<?> javaType = entityType.getJavaType();
        String displayName = customLabel != null ? customLabel : splitCamelCase(entityType.getName());

        FieldDescriptor idField = null;
        FieldDescriptor versionField = null;
        List<FieldDescriptor> fields = new ArrayList<>();
        List<AssociationDescriptor> associations = new ArrayList<>();

        for (Attribute<?, ?> attr : entityType.getAttributes()) {
            Member member = attr.getJavaMember();
            java.lang.reflect.AnnotatedElement field = member instanceof java.lang.reflect.AnnotatedElement element ? element : null;

            if (field != null && field.isAnnotationPresent(AdminIgnore.class)) {
                continue;
            }

            boolean isId = false;
            boolean isEmbeddedId = false;
            boolean isVersion = false;
            boolean isNullable = true;
            Set<String> validationRules = new HashSet<>();
            Long minVal = null;
            Long maxVal = null;
            String adminFieldDescription = null;
            AdminField presentation = field == null ? null : field.getAnnotation(AdminField.class);
            if (presentation != null && !presentation.currency().isBlank()) {
                Currency.getInstance(presentation.currency());
                if (!Number.class.isAssignableFrom(attr.getJavaType())
                        && !Set.of(byte.class, short.class, int.class, long.class, float.class, double.class).contains(attr.getJavaType())) {
                    throw new IllegalArgumentException("Currency requires a numeric field: " + attr.getName());
                }
            }

            if (field != null) {
                isId = field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(EmbeddedId.class);
                isEmbeddedId = field.isAnnotationPresent(EmbeddedId.class);
                isVersion = field.isAnnotationPresent(Version.class);

                if (field.isAnnotationPresent(NotNull.class) ||
                    field.isAnnotationPresent(NotEmpty.class) ||
                    field.isAnnotationPresent(NotBlank.class)) {
                    isNullable = false;
                    validationRules.add("required");
                }
                if (field.isAnnotationPresent(Email.class)) {
                    validationRules.add("email");
                }
                if (field.isAnnotationPresent(Size.class)) {
                    Size size = field.getAnnotation(Size.class);
                    validationRules.add("size:" + size.min() + ":" + size.max());
                }
                if (field.isAnnotationPresent(Min.class)) {
                    minVal = field.getAnnotation(Min.class).value();
                }
                if (field.isAnnotationPresent(Max.class)) {
                    maxVal = field.getAnnotation(Max.class).value();
                }
                if (field.isAnnotationPresent(AdminField.class)) {
                    adminFieldDescription = field.getAnnotation(AdminField.class).description();
                }
            }

            if (attr.isAssociation()) {
                AssociationDescriptor.AssociationType assocType = resolveAssociationType(attr);
                Class<?> targetClass = attr.isCollection()
                        ? ((PluralAttribute<?, ?, ?>) attr).getElementType().getJavaType()
                        : attr.getJavaType();
                String targetSlug = toSlug(targetClass.getSimpleName());

                associations.add(new AssociationDescriptor(
                        attr.getName(),
                        presentation != null && !presentation.label().isBlank() ? presentation.label() : splitCamelCase(attr.getName()),
                        targetClass,
                        targetSlug,
                        assocType
                ));
            } else {
                FieldDescriptor fd = new FieldDescriptor(
                        attr.getName(),
                        presentation != null && !presentation.label().isBlank() ? presentation.label() : splitCamelCase(attr.getName()),
                        attr.getJavaType(),
                        isId,
                        isEmbeddedId,
                        isVersion,
                        isSearchable(attr.getJavaType()),
                        isNullable,
                        validationRules,
                        minVal,
                        maxVal,
                        adminFieldDescription,
                        presentation == null ? 100 : presentation.order(),
                        presentation == null || presentation.showInList(),
                        presentation == null ? "" : presentation.currency(),
                        presentation == null || presentation.group().isBlank() ? "Details" : presentation.group()
                );

                if (isId) {
                    idField = fd;
                }
                if (isVersion) {
                    versionField = fd;
                }
                fields.add(fd);
            }
        }

        fields.sort(Comparator.comparingInt(FieldDescriptor::order).thenComparing(FieldDescriptor::name));
        associations.sort(Comparator.comparing(AssociationDescriptor::name));
        return new EntityDescriptor(
                entityType.getName(),
                slug,
                displayName,
                javaType,
                idField,
                versionField,
                Collections.unmodifiableList(fields),
                Collections.unmodifiableList(associations)
        );
    }

    private AssociationDescriptor.AssociationType resolveAssociationType(Attribute<?, ?> attr) {
        if (!attr.isCollection()) {
            return AssociationDescriptor.AssociationType.MANY_TO_ONE;
        }
        return AssociationDescriptor.AssociationType.ONE_TO_MANY;
    }

    public Optional<EntityDescriptor> getBySlug(String slug) {
        return Optional.ofNullable(descriptorsBySlug.get(slug));
    }

    public Optional<EntityDescriptor> getByClass(Class<?> clazz) {
        return Optional.ofNullable(descriptorsByClass.get(clazz));
    }

    public Collection<EntityDescriptor> getAllDescriptors() {
        return Collections.unmodifiableCollection(descriptorsBySlug.values());
    }

    private boolean isSearchable(Class<?> type) {
        return String.class.equals(type) || Number.class.isAssignableFrom(type) ||
               type.equals(int.class) || type.equals(long.class) ||
               type.equals(double.class) || type.equals(float.class);
    }

    private String toSlug(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private String splitCamelCase(String s) {
        String spaced = s.replaceAll(String.format("%s|%s|%s",
                "(?<=[A-Z])(?=[A-Z][a-z])",
                "(?<=[^A-Z])(?=[A-Z])",
                "(?<=[A-Za-z])(?=[^A-Za-z])"
        ), " ").trim();

        if (spaced.isEmpty()) return spaced;
        String[] words = spaced.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (words[i].length() > 0) {
                sb.append(Character.toUpperCase(words[i].charAt(0)))
                  .append(words[i].substring(1));
                if (i < words.length - 1) sb.append(" ");
            }
        }
        return sb.toString();
    }
}
