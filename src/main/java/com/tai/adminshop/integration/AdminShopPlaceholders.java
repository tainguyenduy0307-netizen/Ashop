package com.tai.adminshop.integration;

import com.mojang.authlib.GameProfile;
import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.economy.CobEcoHook;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;

public final class AdminShopPlaceholders {
    private static final ThreadLocal<DecimalFormat> BALANCE_FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        format.setParseBigDecimal(true);
        return format;
    });

    private AdminShopPlaceholders() {
    }

    public static void register() {
        Placeholders.register(Identifier.of(AdminShopMod.MOD_ID, "balance"), (context, argument) -> balance(context.gameProfile()));
        Placeholders.register(Identifier.of(AdminShopMod.MOD_ID, "gems"), (context, argument) -> gems(context.gameProfile()));
        Placeholders.register(Identifier.of("ashop", "gems"), (context, argument) -> gems(context.gameProfile()));
    }

    private static PlaceholderResult balance(GameProfile profile) {
        UUID uuid = playerUuid(profile);
        if (uuid == null) {
            return PlaceholderResult.invalid("No player");
        }

        BigDecimal balance = CobEcoHook.getBalance(uuid);
        return PlaceholderResult.value(BALANCE_FORMAT.get().format(balance));
    }

    private static PlaceholderResult gems(GameProfile profile) {
        UUID uuid = playerUuid(profile);
        if (uuid == null) {
            return PlaceholderResult.invalid("No player");
        }

        return PlaceholderResult.value(com.tai.adminshop.util.PriceFormatter.integer(AdminShopMod.GEMS_MANAGER.balance(uuid)));
    }

    private static UUID playerUuid(GameProfile profile) {
        return profile == null ? null : profile.getId();
    }
}
