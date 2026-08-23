package com.tai.adminshop.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tai.adminshop.AdminShopMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class GemsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configDir = FabricLoader.getInstance().getConfigDir().resolve("adminshop");
    private final Path file = configDir.resolve("gems.json");
    private GemsData data = new GemsData();

    public synchronized void load() {
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(file)) {
                data = new GemsData();
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(file)) {
                GemsData loaded = GSON.fromJson(reader, GemsData.class);
                data = loaded == null ? new GemsData() : loaded;
                if (data.balances == null) {
                    data.balances = new LinkedHashMap<>();
                }
            }
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to load gems data", e);
            data = new GemsData();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to save gems data", e);
        }
    }

    public synchronized double balance(UUID uuid) {
        return Math.max(0.0D, data.balances.getOrDefault(uuid.toString(), 0.0D));
    }

    public synchronized boolean has(UUID uuid, double amount) {
        return amount >= 0.0D && balance(uuid) >= amount;
    }

    public synchronized boolean give(UUID uuid, double amount) {
        if (!validAmount(amount)) {
            return false;
        }
        set(uuid, balance(uuid) + amount);
        return true;
    }

    public synchronized boolean take(UUID uuid, double amount) {
        if (!validAmount(amount) || !has(uuid, amount)) {
            return false;
        }
        set(uuid, balance(uuid) - amount);
        return true;
    }

    public synchronized boolean set(UUID uuid, double amount) {
        if (!validAmount(amount)) {
            return false;
        }
        data.balances.put(uuid.toString(), amount);
        save();
        return true;
    }

    private static boolean validAmount(double amount) {
        return Double.isFinite(amount) && amount >= 0.0D;
    }

    private static final class GemsData {
        private Map<String, Double> balances = new LinkedHashMap<>();
    }
}
