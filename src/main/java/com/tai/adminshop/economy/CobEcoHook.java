package com.tai.adminshop.economy;

import com.tai.adminshop.AdminShopMod;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

public final class CobEcoHook {
    private CobEcoHook() {
    }

    public static boolean hasMoney(UUID uuid, double amount) {
        BigDecimal balance = getBalance(uuid);
        return balance.compareTo(BigDecimal.valueOf(amount)) >= 0;
    }

    public static boolean takeMoney(UUID uuid, double amount) {
        if (amount < 0 || !hasMoney(uuid, amount)) {
            return false;
        }

        try {
            Object manager = getEconomyManager();
            Method method = findMethod(manager.getClass(), "subtractBalance", UUID.class, BigDecimal.class);
            Object result = method.invoke(manager, uuid, BigDecimal.valueOf(amount));
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (ReflectiveOperationException e) {
            AdminShopMod.LOGGER.error("Failed to subtract Cobblemon Economy balance", e);
            return false;
        }
    }

    public static boolean giveMoney(UUID uuid, double amount) {
        if (amount < 0) {
            return false;
        }

        try {
            Object manager = getEconomyManager();
            Method method = findMethod(manager.getClass(), "addBalance", UUID.class, BigDecimal.class);
            method.invoke(manager, uuid, BigDecimal.valueOf(amount));
            return true;
        } catch (ReflectiveOperationException e) {
            AdminShopMod.LOGGER.error("Failed to add Cobblemon Economy balance", e);
            return false;
        }
    }

    public static BigDecimal getBalance(UUID uuid) {
        try {
            Object manager = getEconomyManager();
            Method method = findMethod(manager.getClass(), "getBalance", UUID.class);
            Object result = method.invoke(manager, uuid);
            if (result instanceof BigDecimal balance) {
                return balance;
            }
        } catch (ReflectiveOperationException e) {
            AdminShopMod.LOGGER.error("Failed to read Cobblemon Economy balance", e);
        }
        return BigDecimal.ZERO;
    }

    private static Object getEconomyManager() throws ReflectiveOperationException {
        Class<?> economyClass = Class.forName("com.cobblemon.economy.fabric.CobblemonEconomy");
        Method method = economyClass.getMethod("getEconomyManager");
        return method.invoke(null);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        }
    }
}
