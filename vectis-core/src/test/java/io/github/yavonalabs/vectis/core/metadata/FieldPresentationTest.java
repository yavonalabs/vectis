package io.github.yavonalabs.vectis.core.metadata;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class FieldPresentationTest {
    private FieldDescriptor field(String currency) {
        return new FieldDescriptor("amount", "Amount", BigDecimal.class, false, false,
                false, false, true, Set.of(), null, null, "", 100, true, currency);
    }
    @Test void ordinaryDecimalsDoNotBecomeMoneyOrLosePrecision() {
        assertThat(field("").format(new BigDecimal("1234.56789"))).isEqualTo("1,234.56789");
        assertThat(field("").format(null)).isEqualTo("Not provided");
    }
    @Test void declaredCurrencyUsesAnUnambiguousCode() {
        assertThat(field("USD").format(new BigDecimal("120000.00"))).isEqualTo("120,000.00 USD");
        assertThat(field("INR").format(new BigDecimal("1000.5"))).isEqualTo("1,000.50 INR");
        assertThat(field("JPY").format(new BigDecimal("1000"))).isEqualTo("1,000 JPY");
    }
    enum State { ON_LEAVE, PENDING_REVIEW }
    @Test void statusesAndBooleansHaveReadableLabels() {
        assertThat(field("").format(State.PENDING_REVIEW)).isEqualTo("Pending review");
        assertThat(field("").format(true)).isEqualTo("Yes");
    }
}
