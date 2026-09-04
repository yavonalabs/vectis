package io.github.yavonalabs.vectis.core.metadata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Set;

public record FieldDescriptor(
    String name,
    String displayName,
    Class<?> type,
    boolean isId,
    boolean isEmbeddedId,
    boolean isVersion,
    boolean isSearchable,
    boolean isNullable,
    Set<String> validationRules,
    Long min,
    Long max,
    String description
) {
    public boolean isString() {
        return String.class.equals(type) || CharSequence.class.isAssignableFrom(type);
    }

    public boolean isBoolean() {
        return Boolean.class.equals(type) || boolean.class.equals(type);
    }

    public boolean isEnum() {
        return type.isEnum();
    }

    public boolean isNumeric() {
        return Number.class.isAssignableFrom(type) ||
               type.equals(int.class) || type.equals(long.class) ||
               type.equals(double.class) || type.equals(float.class);
    }

    public boolean isDecimal() {
        return BigDecimal.class.equals(type) || Double.class.equals(type) ||
               Float.class.equals(type) || double.class.equals(type) || float.class.equals(type);
    }

    public boolean isDateOrTime() {
        return LocalDate.class.equals(type) || LocalDateTime.class.equals(type) ||
               ZonedDateTime.class.equals(type) || Date.class.isAssignableFrom(type);
    }

    public Object[] getEnumConstants() {
        return type.isEnum() ? type.getEnumConstants() : new Object[0];
    }
}
