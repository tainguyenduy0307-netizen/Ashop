package com.tai.adminshop.economy;

import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.config.ShopEntry;
import com.tai.adminshop.util.PriceFormatter;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;

public final class Currency {
    public static final String DOLLARS = "dollars";
    public static final String GEMS = "gems";

    private Currency() {
    }

    public static String normalize(String currency) {
        if (currency == null || currency.isBlank()) {
            return DOLLARS;
        }
        String normalized = currency.trim().toLowerCase(Locale.ROOT);
        if ("money".equals(normalized) || "dollar".equals(normalized) || "cobbledollars".equals(normalized)
                || "pokedollars".equals(normalized)) {
            return DOLLARS;
        }
        if (GEMS.equals(normalized) || "gem".equals(normalized)) {
            return GEMS;
        }
        return DOLLARS;
    }

    public static String of(ShopEntry entry) {
        String normalized = normalize(entry.currency);
        entry.currency = normalized;
        return normalized;
    }

    public static boolean isGems(String currency) {
        return GEMS.equals(normalize(currency));
    }

    public static boolean has(ServerPlayerEntity player, String currency, double amount) {
        if (isGems(currency)) {
            return AdminShopMod.GEMS_MANAGER.has(player.getUuid(), amount);
        }
        return CobEcoHook.hasMoney(player.getUuid(), amount);
    }

    public static boolean take(ServerPlayerEntity player, String currency, double amount) {
        if (isGems(currency)) {
            return AdminShopMod.GEMS_MANAGER.take(player.getUuid(), amount);
        }
        return CobEcoHook.takeMoney(player.getUuid(), amount);
    }

    public static boolean give(ServerPlayerEntity player, String currency, double amount) {
        if (isGems(currency)) {
            return AdminShopMod.GEMS_MANAGER.give(player.getUuid(), amount);
        }
        return CobEcoHook.giveMoney(player.getUuid(), amount);
    }

    public static String format(String currency, double amount) {
        if (isGems(currency)) {
            return PriceFormatter.integer(amount) + " Gems";
        }
        return PriceFormatter.money(amount) + " PokeDollars";
    }
}
