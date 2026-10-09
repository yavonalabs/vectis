package io.github.yavonalabs.vectis.core.web;

import io.github.yavonalabs.vectis.core.metadata.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AppliedFiltersTest {
    @Test void removingOneConditionRetainsOthersSortAndSearchButResetsPage() {
        var field = new FieldDescriptor("name", "Name", String.class, false, false, false, true, true, Set.of(), null, null, "");
        var descriptor = new EntityDescriptor("Entry", "entry", "Entries", Object.class, null, null, List.of(field), List.of());
        var chips = AppliedFilters.chips(descriptor, Map.of("filterField", List.of("name", "name"), "filterOp", List.of("contains", "ne"),
                "filterValue", List.of("a&b", "c"), "search", List.of("needle"), "sort", List.of("name"), "dir", List.of("desc"), "page", List.of("9")));
        assertThat(chips).hasSize(3);
        assertThat(chips.get(1).get("query")).contains("filterValue=c", "search=needle", "dir=desc", "sort=name").doesNotContain("page=", "a%26b");
        assertThat(chips.get(2).get("query")).contains("filterValue=a%26b").doesNotContain("filterValue=c");
    }
}
