package com.tai.adminshop.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CurrencyTest {
    @Test void legacyGemsNormalizeToRubyAndUnknownFailsClosed() {
        assertEquals(Currency.RUBY, Currency.normalize("gems"));
        assertEquals(Currency.RUBY, Currency.normalize("gem"));
        assertEquals(Currency.SAPPHIRE, Currency.normalize("sapphire"));
        assertEquals(Currency.MONEY, Currency.normalize("money"));
        assertEquals("", Currency.normalize("rubyy"));
    }

    @Test void sapphireUsesSapphireFirstAndRubyNeverUsesSapphire() {
        assertEquals(new PaymentPlan(100, 0), PaymentPlan.forPrice("sapphire", 100, 100, 0));
        assertEquals(new PaymentPlan(70, 30), PaymentPlan.forPrice("sapphire", 100, 70, 50));
        assertEquals(new PaymentPlan(0, 100), PaymentPlan.forPrice("sapphire", 100, 0, 100));
        assertEquals(new PaymentPlan(0, 100), PaymentPlan.forPrice("ruby", 100, 500, 100));
        assertNull(PaymentPlan.forPrice("ruby", 100, 500, 80));
        assertNull(PaymentPlan.forPrice("sapphire", 100.5, 500, 500));
    }
}
