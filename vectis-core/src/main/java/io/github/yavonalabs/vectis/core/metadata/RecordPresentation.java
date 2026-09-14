package io.github.yavonalabs.vectis.core.metadata;

import org.springframework.beans.PropertyAccessorFactory;

/** Labels never delegate to an entity's potentially sensitive toString(). */
public final class RecordPresentation {
    private RecordPresentation() {}
    public static String label(EntityDescriptor descriptor, Object entity) {
        var wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        for (String name : new String[]{"displayName", "name", "firstName", "email"}) {
            if (descriptor.fields().stream().anyMatch(f -> f.name().equals(name) && f.isString())) {
                Object value = wrapper.getPropertyValue(name);
                if (value instanceof String text && !text.isBlank()) {
                    if (name.equals("firstName") && descriptor.fields().stream().anyMatch(f -> f.name().equals("lastName") && f.isString())) {
                        Object lastName = wrapper.getPropertyValue("lastName");
                        if (lastName instanceof String last && !last.isBlank()) return text + " " + last;
                    }
                    return text;
                }
            }
        }
        Object id = descriptor.idField() == null ? null : wrapper.getPropertyValue(descriptor.idField().name());
        return descriptor.displayName() + (id == null ? "" : " #" + id);
    }
}
