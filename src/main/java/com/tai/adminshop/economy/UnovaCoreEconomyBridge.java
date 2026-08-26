package com.tai.adminshop.economy;

import com.tai.adminshop.AdminShopMod;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Optional runtime bridge: AShop never reads or writes UnovaCore balance files. */
public final class UnovaCoreEconomyBridge {
    private UnovaCoreEconomyBridge() { }

    public static OptionalLong balance(UUID playerId, String currency) {
        try {
            Object provider = provider(currency);
            if (provider == null) return OptionalLong.empty();
            Object result = provider.getClass().getMethod("getBalance", UUID.class).invoke(provider, playerId);
            return result instanceof OptionalLong value ? value : OptionalLong.empty();
        } catch (ReflectiveOperationException | LinkageError exception) {
            return OptionalLong.empty();
        }
    }

    public static boolean isProviderAvailable(String currency) {
        try { return provider(currency) != null; }
        catch (ReflectiveOperationException | LinkageError exception) { return false; }
    }

    public static boolean has(UUID playerId, String currency, long amount) {
        OptionalLong balance = balance(playerId, currency);
        return amount >= 0 && balance.isPresent() && balance.getAsLong() >= amount;
    }

    public static boolean withdraw(UUID playerId, String currency, long amount) {
        return mutate(playerId, currency, amount, "withdraw");
    }

    public static boolean deposit(UUID playerId, String currency, long amount) {
        return mutate(playerId, currency, amount, "deposit");
    }

    public static boolean setRuby(UUID playerId, long amount) {
        if (amount < 0) return false;
        OptionalLong current = balance(playerId, "ruby");
        if (current.isEmpty()) return false;
        return amount >= current.getAsLong()
                ? (amount == current.getAsLong() || deposit(playerId, "ruby", amount - current.getAsLong()))
                : withdraw(playerId, "ruby", current.getAsLong() - amount);
    }

    private static boolean mutate(UUID playerId, String currency, long amount, String methodName) {
        if (amount <= 0) return false;
        try {
            Object provider = provider(currency);
            if (provider == null) return false;
            Object result = provider.getClass().getMethod(methodName, UUID.class, long.class).invoke(provider, playerId, amount);
            return result != null && Boolean.TRUE.equals(result.getClass().getMethod("successful").invoke(result));
        } catch (ReflectiveOperationException | LinkageError exception) {
            AdminShopMod.LOGGER.debug("UnovaCore {} {} failed for {}", currency, methodName, playerId, exception);
            return false;
        }
    }

    private static Object provider(String currency) throws ReflectiveOperationException {
        Class<?> core = Class.forName("com.unova.core.UnovaCore");
        Object registry = core.getMethod("economyProviderRegistry").invoke(null);
        Object result = registry.getClass().getMethod("provider", String.class).invoke(registry, currency);
        if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return null;
        return optional.get();
    }
}
