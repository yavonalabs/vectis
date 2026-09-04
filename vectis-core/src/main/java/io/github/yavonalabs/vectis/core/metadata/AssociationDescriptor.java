package io.github.yavonalabs.vectis.core.metadata;

public record AssociationDescriptor(
    String name,
    String displayName,
    Class<?> targetEntityClass,
    String targetEntitySlug,
    AssociationType associationType
) {
    public enum AssociationType {
        MANY_TO_ONE,
        ONE_TO_MANY,
        MANY_TO_MANY,
        ONE_TO_ONE
    }

    public boolean isSingleValued() {
        return associationType == AssociationType.MANY_TO_ONE || associationType == AssociationType.ONE_TO_ONE;
    }
}