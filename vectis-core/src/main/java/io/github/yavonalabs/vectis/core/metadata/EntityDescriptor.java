package io.github.yavonalabs.vectis.core.metadata;

import java.util.List;

public record EntityDescriptor(
    String name,
    String slug,
    String displayName,
    Class<?> javaType,
    FieldDescriptor idField,
    FieldDescriptor versionField,
    List<FieldDescriptor> fields,
    List<AssociationDescriptor> associations
) {
    public String singularName() {
        var annotation = javaType.getAnnotation(io.github.yavonalabs.vectis.core.annotation.AdminEntity.class);
        return annotation != null && !annotation.singularLabel().isBlank()
                ? annotation.singularLabel() : name.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    public List<FieldDescriptor> listFields() {
        return fields.stream().filter(f -> !f.isId() && !f.isVersion() && f.showInList()).toList();
    }

    public boolean hasVersion() {
        return versionField != null;
    }
}
