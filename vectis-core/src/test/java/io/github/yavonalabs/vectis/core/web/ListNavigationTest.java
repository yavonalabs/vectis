package io.github.yavonalabs.vectis.core.web;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ListNavigationTest {
    @Test void retainsRepeatedFiltersAndEscapesValues() {
        String query = "page=2&filterField=status&filterField=salary&filterOp=eq&filterOp=gte&filterValue=ACTIVE&filterValue=90000&search=A%26B";
        assertThat(ListNavigation.sanitize(query)).isEqualTo(query);
        assertThat(ListNavigation.contextSuffix(query)).startsWith("?_list=page%3D2%26");
    }
    @Test void rejectsUnsafeOrUnboundedContext() {
        for (String query : new String[]{"search=%ZZ", "search=%0d%0aLocation%3a", "search=" + "a".repeat(201), "page=1&".repeat(16)})
            assertThat(ListNavigation.sanitize(query)).isEmpty();
        assertThat(ListNavigation.sanitize("redirect=https://evil.example&_list=recursive&page=2")).isEqualTo("page=2");
        assertThat(ListNavigation.querySuffix("https://evil.example")).isEmpty();
    }
}
