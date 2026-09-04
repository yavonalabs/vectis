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
    public boolean hasVersion() {
        return versionField != null;
    }
}