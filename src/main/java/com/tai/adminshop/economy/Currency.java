package com.tai.adminshop.economy;

import com.tai.adminshop.config.ShopEntry;
import com.tai.adminshop.util.PriceFormatter;

import java.util.Locale;

public final class Currency {
    public static final String MONEY = "money";
    public static final String SAPPHIRE = "sapphire";
    public static final String RUBY = "ruby";
    /** @deprecated use MONEY. */ public static final String DOLLARS = MONEY;

    private Currency() {
    }

    public static String normalize(String currency) {
        if (currency == null || currency.isBlank()) {
            return MONEY;
        }
        String normalized = currency.trim().toLowerCase(Locale.ROOT);
        if ("money".equals(normalized) || "dollar".equals(normalized) || "cobbledollars".equals(normalized)
                || "pokedollars".equals(normalized)) {
            return MONEY;
        }
        if ("sapphire".equals(normalized) || "sapphires".equals(normalized) || "pco".equals(normalized)) {
            return SAPPHIRE;
        }
        if (RUBY.equals(normalized) || "gems".equals(normalized) || "gem".equals(normalized)) return RUBY;
        return "";
    }

    public static String of(ShopEntry entry) {
        String normalized = normalize(entry.currency);
        entry.currency = normalized;
        return normalized;
    }

    public static boolean isRuby(String currency) {
        return RUBY.equals(normalize(currency));
    }

    public static String format(String currency, double amount) {
        return switch (normalize(currency)) {
            case SAPPHIRE -> PriceFormatter.integer(amount) + " Sapphire";
            case RUBY -> PriceFormatter.integer(amount) + " Ruby";
            case MONEY -> PriceFormatter.money(amount) + " PokeDollars";
            default -> "Invalid currency";
        };
    }
}
