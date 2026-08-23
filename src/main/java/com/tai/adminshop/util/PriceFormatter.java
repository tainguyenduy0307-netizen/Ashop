package com.tai.adminshop.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class PriceFormatter {
    private static final ThreadLocal<DecimalFormat> FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        return new DecimalFormat("#,##0.00", symbols);
    });

    private PriceFormatter() {
    }

    public static String money(double value) {
        return FORMAT.get().format(value);
    }

    public static String integer(double value) {
        return String.format(Locale.US, "%,d", Math.round(value));
    }
}
