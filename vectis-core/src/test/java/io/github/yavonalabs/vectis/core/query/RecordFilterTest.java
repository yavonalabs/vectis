package io.github.yavonalabs.vectis.core.query;

import io.github.yavonalabs.vectis.core.metadata.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RecordFilterTest {
    private EntityDescriptor descriptor(Class<?> type) {
        var field = new FieldDescriptor("value", "Value", type, false, false, false, true, true, Set.of(), null, null, "");
        return new EntityDescriptor("Test", "test", "Test", Object.class, null, null, List.of(field), List.of());
    }
    @Test void datesAreTypedAndInvalidCalendarDatesAreRejected() {
        var result = RecordFilter.parse(descriptor(LocalDate.class), List.of("value"), List.of("gte"), List.of("2026-09-14"));
        assertThat(result.get(0).value()).isEqualTo(LocalDate.of(2026,9,14));
        assertThatThrownBy(() -> RecordFilter.parse(descriptor(LocalDate.class), List.of("value"), List.of("eq"), List.of("2026-02-30")))
                .isInstanceOf(ResponseStatusException.class);
    }
    @Test void booleansRejectAmbiguousValuesAndNumericComparisons() {
        assertThat(RecordFilter.parse(descriptor(Boolean.class), List.of("value"), List.of("eq"), List.of("false")).get(0).value()).isEqualTo(false);
        assertThatThrownBy(() -> RecordFilter.parse(descriptor(Boolean.class), List.of("value"), List.of("eq"), List.of("anything")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> RecordFilter.parse(descriptor(Boolean.class), List.of("value"), List.of("gt"), List.of("false")))
                .isInstanceOf(ResponseStatusException.class);
    }
    @Test void blankRowsAndNullChecksDoNotRequireAValue() {
        assertThat(RecordFilter.parse(descriptor(String.class), List.of("", "value"), List.of("eq", "empty"), List.of("", "")))
                .containsExactly(new RecordFilter("value", "empty", null));
    }
}
