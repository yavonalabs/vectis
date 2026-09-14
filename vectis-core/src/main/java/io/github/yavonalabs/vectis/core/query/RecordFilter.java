package io.github.yavonalabs.vectis.core.query;

import io.github.yavonalabs.vectis.core.metadata.EntityDescriptor;
import io.github.yavonalabs.vectis.core.metadata.FieldDescriptor;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** A validated scalar condition. All conditions are combined with AND. */
public record RecordFilter(String field, String operator, Object value) {
    public static List<RecordFilter> parse(EntityDescriptor descriptor, List<String> fields,
            List<String> operators, List<String> values) {
        if (fields.size() > 3 || fields.size() != operators.size() || fields.size() != values.size())
            throw invalid("Supply at most three complete filter conditions.");
        List<RecordFilter> filters = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            String name = fields.get(i), op = operators.get(i), raw = values.get(i).trim();
            if (name.isBlank() && raw.isBlank()) continue;
            FieldDescriptor field = descriptor.fields().stream()
                    .filter(f -> f.name().equals(name) && supported(f)).findFirst()
                    .orElseThrow(() -> invalid("Choose an available field to filter."));
            if (raw.length() > 200) throw invalid("Filter values must be at most 200 characters.");
            if (op.equals("empty") || op.equals("notEmpty")) {
                filters.add(new RecordFilter(name, op, null));
                continue;
            }
            if (!Set.of("eq", "ne", "contains", "gt", "gte", "lt", "lte").contains(op))
                throw invalid("Choose an available filter condition.");
            if (op.equals("contains") && !field.isString()) throw invalid("Contains is available only for text fields.");
            if (Set.of("gt", "gte", "lt", "lte").contains(op)
                    && !field.isNumeric() && field.type() != LocalDate.class && field.type() != LocalDateTime.class)
                throw invalid("Comparison conditions require a number or date.");
            if (raw.isBlank()) throw invalid("Enter a value for " + field.displayName() + ".");
            try {
                Object value;
                if (field.isEnum()) {
                    String enumName = raw.replace(' ', '_').toUpperCase(Locale.ROOT);
                    value = Arrays.stream(field.getEnumConstants()).filter(e -> ((Enum<?>) e).name().equals(enumName))
                            .findFirst().orElseThrow();
                } else if (field.isBoolean()) {
                    if (!Set.of("true", "false").contains(raw)) throw new IllegalArgumentException();
                    value = Boolean.valueOf(raw);
                } else if (field.type() == LocalDate.class) value = LocalDate.parse(raw);
                else if (field.type() == LocalDateTime.class) value = LocalDateTime.parse(raw);
                else {
                    if (field.isNumeric()) new java.math.BigDecimal(raw); // Reject non-finite values.
                    value = DefaultConversionService.getSharedInstance().convert(raw, field.type());
                }
                filters.add(new RecordFilter(name, op, value));
            } catch (Exception ex) { throw invalid("Enter a valid value for " + field.displayName() + "."); }
        }
        return List.copyOf(filters);
    }

    public static boolean supported(FieldDescriptor field) {
        return !field.isId() && !field.isVersion() && (field.isString() || field.isEnum()
                || field.isBoolean() || field.isNumeric() || field.type() == LocalDate.class || field.type() == LocalDateTime.class);
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
