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
    String description,
    int order,
    boolean showInList,
    String currency
) {
    public FieldDescriptor(String name, String displayName, Class<?> type, boolean isId,
            boolean isEmbeddedId, boolean isVersion, boolean isSearchable, boolean isNullable,
            Set<String> validationRules, Long min, Long max, String description) {
        this(name, displayName, type, isId, isEmbeddedId, isVersion, isSearchable,
                isNullable, validationRules, min, max, description, 100, true, "");
    }

    public String format(Object value) {
        if (value == null) return "Not provided";
        if (value instanceof Boolean flag) return flag ? "Yes" : "No";
        if (value instanceof Enum<?> e) {
            String label = e.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
            return Character.toUpperCase(label.charAt(0)) + label.substring(1);
        }
        if (value instanceof Number number) {
            var formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale.US);
            formatter.setMaximumFractionDigits(340);
            if (currency != null && !currency.isBlank()) {
                int digits = java.util.Currency.getInstance(currency).getDefaultFractionDigits();
                formatter.setMinimumFractionDigits(Math.max(0, digits));
                return formatter.format(number) + " " + currency;
            }
            return formatter.format(number);
        }
        return String.valueOf(value);
    }

    public boolean isString() {
        return String.class.equals(type) || CharSequence.class.isAssignableFrom(type);
    }

    public String filterKind() {
        if (isEnum()) return "enum";
        if (isBoolean()) return "boolean";
        if (isNumeric()) return "number";
        if (type == LocalDate.class) return "date";
        if (type == LocalDateTime.class) return "datetime-local";
        return "text";
    }

    public String filterChoices() {
        return isEnum() ? java.util.Arrays.stream(getEnumConstants())
                .map(value -> ((Enum<?>) value).name()).collect(java.util.stream.Collectors.joining("|")) : "";
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
