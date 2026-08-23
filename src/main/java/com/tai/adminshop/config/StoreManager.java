package com.tai.adminshop.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.economy.Currency;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StoreManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_ROWS = 3;
    private static final String DEFAULT_TRACK = "ranks";

    private final Path configDir = FabricLoader.getInstance().getConfigDir().resolve("adminshop");
    private final Path file = configDir.resolve("store.json");
    private StoreConfig config = new StoreConfig();

    public synchronized void load() {
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(file)) {
                config = defaultConfig();
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(file)) {
                StoreConfig loaded = GSON.fromJson(reader, StoreConfig.class);
                config = loaded == null ? defaultConfig() : loaded;
            }
            normalize();
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to load store config", e);
            config = defaultConfig();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to save store config", e);
        }
    }

    public synchronized StoreConfig config() {
        return config;
    }

    public synchronized void add(StoreEntry entry) {
        normalizeEntry(entry);
        if (entry.id == null || entry.id.isBlank()) {
            entry.id = "store_item_" + System.currentTimeMillis();
        }
        config.items.removeIf(existing -> existing.id != null && existing.id.equalsIgnoreCase(entry.id));
        if (entry.slot < 0 || entry.slot >= rowsToSize(config.rows) || isSlotUsed(entry.slot)) {
            entry.slot = firstAvailableSlot();
        }
        config.items.add(entry);
        save();
    }

    private void normalize() {
        if (config.command == null || config.command.isBlank()) {
            config.command = "store";
        }
        if (config.title == null || config.title.isBlank()) {
            config.title = "Gem Store";
        }
        if (config.rows < 1 || config.rows > 6) {
            config.rows = DEFAULT_ROWS;
        }
        if (config.items == null) {
            config.items = new java.util.ArrayList<>();
        }
        boolean changed = false;
        for (StoreEntry entry : config.items) {
            changed |= normalizeEntry(entry);
        }
        for (StoreEntry defaultRank : defaultRankEntries()) {
            if (config.items.stream().noneMatch(entry -> defaultRank.id.equalsIgnoreCase(entry.id))) {
                config.items.add(defaultRank);
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    private static boolean normalizeEntry(StoreEntry entry) {
        boolean changed = false;
        String currency = Currency.normalize(entry.currency);
        if (!currency.equals(entry.currency)) {
            entry.currency = currency;
            changed = true;
        }
        if (entry.displayItem == null || entry.displayItem.isBlank()) {
            entry.displayItem = "minecraft:stone";
            changed = true;
        }
        if (entry.lore == null) {
            entry.lore = new java.util.ArrayList<>();
            changed = true;
        }
        if (entry.commands == null) {
            entry.commands = new java.util.ArrayList<>();
            changed = true;
        }
        if (entry.track == null || entry.track.isBlank()) {
            entry.track = DEFAULT_TRACK;
            changed = true;
        }
        return changed;
    }

    private int firstAvailableSlot() {
        Set<Integer> used = new HashSet<>();
        for (StoreEntry entry : config.items) {
            used.add(entry.slot);
        }
        int size = rowsToSize(config.rows);
        for (int slot = 0; slot < size; slot++) {
            if (!used.contains(slot)) {
                return slot;
            }
        }
        return Math.max(0, size - 1);
    }

    private boolean isSlotUsed(int slot) {
        for (StoreEntry entry : config.items) {
            if (entry.slot == slot) {
                return true;
            }
        }
        return false;
    }

    public static int rowsToSize(int rows) {
        return Math.max(1, Math.min(6, rows)) * 9;
    }

    private static StoreConfig defaultConfig() {
        StoreConfig config = new StoreConfig();
        config.items = new java.util.ArrayList<>(defaultRankEntries());
        return config;
    }

    private static List<StoreEntry> defaultRankEntries() {
        return List.of(
                rank("default_trainer", 8, "minecraft:copper_ingot", "Default -> Trainer", 100, "default"),
                rank("trainer_ace", 10, "minecraft:iron_ingot", "Trainer -> Ace", 300, "trainer"),
                rank("ace_elite", 12, "minecraft:gold_ingot", "Ace -> Elite", 700, "ace"),
                rank("elite_champion", 14, "minecraft:diamond", "Elite -> Champion", 1200, "elite"),
                rank("champion_master", 16, "minecraft:nether_star", "Champion -> Master", 2000, "champion"),
                rank("master_celes", 22, "minecraft:amethyst_shard", "Master -> Celes", 3000, "master")
        );
    }

    private static StoreEntry rank(String id, int slot, String displayItem, String name, double price,
                                   String requiredGroup) {
        StoreEntry entry = new StoreEntry();
        entry.id = id;
        entry.slot = slot;
        entry.displayItem = displayItem;
        entry.name = name;
        entry.lore = List.of("Price: " + Math.round(price) + " Gems", "Upgrade to the next rank");
        entry.price = price;
        entry.currency = Currency.GEMS;
        entry.requiredGroup = requiredGroup;
        entry.track = DEFAULT_TRACK;
        entry.commands = List.of();
        return entry;
    }
}
