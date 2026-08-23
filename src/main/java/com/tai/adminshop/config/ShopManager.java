package com.tai.adminshop.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.economy.Currency;
import com.tai.adminshop.util.ItemStackSerializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ShopManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final String[] DEFAULT_CATEGORY_FILES = {
            "blocks", "ores", "foods", "farming", "redstone", "decoration", "workstations",
            "enchanting", "dyes", "music", "potions", "mobs", "tools", "combat", "misc", "tickets"
    };
    public static final Set<String> ALLOWED_CATEGORY_IDS = Set.of(DEFAULT_CATEGORY_FILES);
    public static final int[] ALLOWED_ITEM_SLOT_ORDER = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    public static final Set<Integer> ALLOWED_ITEM_SLOTS = Set.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    );
    public static final Set<Integer> RESERVED_CONTROL_SLOTS = Set.of(45, 49, 53);

    private final Path configDir = FabricLoader.getInstance().getConfigDir().resolve("adminshop");
    private final Path configFile = configDir.resolve("shops.json");
    private final Path categoriesDir = configDir.resolve("categories");
    private ShopConfig config = new ShopConfig();
    private Map<String, CategoryShopConfig> categoryConfigs = new LinkedHashMap<>();

    public synchronized void load() {
        try {
            Files.createDirectories(configDir);
            if (Files.exists(configFile)) {
                try (Reader reader = Files.newBufferedReader(configFile)) {
                    ShopConfig loaded = GSON.fromJson(reader, ShopConfig.class);
                    config = loaded == null ? new ShopConfig() : loaded;
                }
            } else {
                config = new ShopConfig();
            }

            if (config.items == null) {
                config.items = new ArrayList<>();
            }

            if (config.items.isEmpty()) {
                save();
                loadCategoryConfigs();
                return;
            }

            boolean changed = false;
            for (ShopEntry entry : config.items) {
                String originalCategory = entry.category;
                entry.category = ShopEntry.normalizeCategory(entry.category);
                if (originalCategory == null || !originalCategory.equals(entry.category)) {
                    changed = true;
                }
                if (entry.dynamicPricing && (entry.priceGroup == null || entry.priceGroup.isBlank())) {
                    entry.priceGroup = entry.category;
                    changed = true;
                }
                String currency = Currency.normalize(entry.currency);
                if (!currency.equals(entry.currency)) {
                    entry.currency = currency;
                    changed = true;
                }
            }

            if (changed) {
                save();
            }
            loadCategoryConfigs();
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to load AdminShop config", e);
            config = new ShopConfig();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to save AdminShop config", e);
        }
    }

    public synchronized void addOrReplace(ShopEntry entry) {
        entry.category = ShopEntry.normalizeCategory(entry.category);
        entry.currency = Currency.normalize(entry.currency);
        CategoryShopConfig categoryConfig = getOrCreateCategoryConfig(entry.category);
        ShopEntry previous = null;
        for (ShopEntry item : categoryConfig.items) {
            if (item.id != null && item.id.equalsIgnoreCase(entry.id)) {
                previous = item;
                break;
            }
        }
        categoryConfig.items.removeIf(item -> item.id != null && item.id.equalsIgnoreCase(entry.id));
        if (entry.quantity <= 0) {
            entry.quantity = 1;
        }
        if (entry.page <= 0) {
            entry.page = 1;
        }
        if (!isAllowedItemSlot(entry.slot) || isSlotUsed(categoryConfig, entry.page, entry.slot)) {
            if (previous != null && previous.page > 0 && isAllowedItemSlot(previous.slot)
                    && !isSlotUsed(categoryConfig, previous.page, previous.slot)) {
                entry.page = previous.page;
                entry.slot = previous.slot;
            } else {
                Placement placement = firstAvailableSlot(categoryConfig);
                entry.page = placement.page;
                entry.slot = placement.slot;
            }
        }
        categoryConfig.items.add(entry);
        saveCategoryConfig(categoryConfig);
        categoryConfigs.put(categoryConfig.id, categoryConfig);
    }

    public synchronized boolean remove(String id) {
        for (CategoryShopConfig categoryConfig : categoryConfigs.values()) {
            boolean removed = categoryConfig.items.removeIf(item -> item.id != null && item.id.equalsIgnoreCase(id));
            if (removed) {
                saveCategoryConfig(categoryConfig);
                return true;
            }
        }

        boolean legacyRemoved = config.items.removeIf(item -> item.id != null && item.id.equalsIgnoreCase(id));
        if (legacyRemoved) {
            save();
        }
        return legacyRemoved;
    }

    public synchronized boolean updatePrice(String id, double buyPrice, double sellPrice) {
        Optional<ShopEntry> entry = get(id);
        if (entry.isEmpty()) {
            return false;
        }

        ShopEntry found = entry.get();
        found.buyPrice = buyPrice;
        found.sellPrice = sellPrice;
        saveEntryOwner(found);
        return true;
    }

    public synchronized boolean updateCategory(String id, String category) {
        Optional<ShopEntry> entry = get(id);
        if (entry.isEmpty()) {
            return false;
        }

        ShopEntry found = entry.get();
        remove(found.id);
        found.category = ShopEntry.normalizeCategory(category);
        addOrReplace(found);
        return true;
    }

    public synchronized boolean updatePurchaseLimit(String id, boolean enabled, int limit,
                                                    PurchaseLimitPeriod period, String label) {
        Optional<ShopEntry> entry = get(id);
        if (entry.isEmpty()) {
            return false;
        }

        ShopEntry found = entry.get();
        found.purchaseLimitEnabled = enabled;
        found.purchaseLimit = enabled ? limit : 0;
        found.purchaseLimitPeriod = enabled && period != null ? period.name() : PurchaseLimitPeriod.NONE.name();
        found.purchaseLimitLabel = enabled ? label : null;
        saveEntryOwner(found);
        return true;
    }

    public synchronized Path backup() throws IOException {
        Files.createDirectories(configDir);
        if (!Files.exists(configFile)) {
            save();
        }

        Path backupDir = configDir.resolve("backups");
        Files.createDirectories(backupDir);
        Path backupFile = backupDir.resolve("shops-" + LocalDateTime.now().format(BACKUP_FORMAT) + ".json");
        Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        return backupFile;
    }

    public synchronized Path backupCategories() throws IOException {
        Files.createDirectories(categoriesDir);
        Path backupDir = configDir.resolve("backups").resolve("categories-" + LocalDateTime.now().format(BACKUP_FORMAT));
        Files.createDirectories(backupDir);
        try (var stream = Files.list(categoriesDir)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                Files.copy(file, backupDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return backupDir;
    }

    public synchronized boolean isEmpty() {
        return config.items.isEmpty() && categoryConfigs.isEmpty();
    }

    public synchronized void replaceWithVanillaFullKeepingTickets(Collection<ShopEntry> generatedEntries) {
        ArrayList<ShopEntry> preserved = new ArrayList<>();
        for (ShopEntry entry : config.items) {
            String category = ShopEntry.normalizeCategory(entry.category);
            if ("tickets".equals(category) || "crates".equals(category)) {
                preserved.add(entry);
            }
        }

        config.items = new ArrayList<>(generatedEntries);
        config.items.addAll(preserved);
        config.items.sort(Comparator
                .comparing((ShopEntry item) -> item.category == null ? "misc" : item.category)
                .thenComparing(item -> item.id == null ? "" : item.id));
        save();
    }

    private CategoryShopConfig getOrCreateCategoryConfig(String category) {
        String id = ShopEntry.normalizeCategory(category);
        if (!ALLOWED_CATEGORY_IDS.contains(id)) {
            id = "misc";
        }
        CategoryShopConfig existing = categoryConfigs.get(id);
        if (existing != null) {
            return existing;
        }

        CategoryShopConfig categoryConfig = new CategoryShopConfig();
        categoryConfig.id = id;
        categoryConfig.displayName = ShopCategory.displayName(id);
        categoryConfig.title = categoryConfig.displayName + " Page %page%/%maxPage%";
        categoryConfig.icon = iconFor(id);
        categoryConfigs.put(id, categoryConfig);
        return categoryConfig;
    }

    private void saveEntryOwner(ShopEntry entry) {
        for (CategoryShopConfig categoryConfig : categoryConfigs.values()) {
            for (ShopEntry item : categoryConfig.items) {
                if (item == entry) {
                    saveCategoryConfig(categoryConfig);
                    return;
                }
            }
        }
        save();
    }

    private void saveCategoryConfig(CategoryShopConfig categoryConfig) {
        try {
            Files.createDirectories(categoriesDir);
            Path file = categoriesDir.resolve(categoryConfig.id + ".json");
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(categoryConfig, writer);
            }
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to save AdminShop category config {}", categoryConfig.id, e);
        }
    }

    public synchronized int fixVanillaCategories() {
        int fixed = 0;
        for (ShopEntry entry : config.items) {
            String currentCategory = ShopEntry.normalizeCategory(entry.category);
            if ("tickets".equals(currentCategory) || "crates".equals(currentCategory) || entry.itemData == null) {
                continue;
            }

            Identifier identifier = itemIdentifier(entry);
            if (identifier == null || !"minecraft".equals(identifier.getNamespace())) {
                continue;
            }

            String itemId = identifier.getPath();
            String detectedCategory = DefaultShopGenerator.detectCategory(itemId);
            boolean changed = false;
            if (!detectedCategory.equals(currentCategory)) {
                entry.category = detectedCategory;
                changed = true;
            }
            if (entry.priceGroup == null || entry.priceGroup.isBlank()) {
                entry.priceGroup = DefaultShopGenerator.detectPriceGroup(itemId, detectedCategory);
                changed = true;
            }
            if (changed) {
                fixed++;
            }
        }

        if (fixed > 0) {
            config.items.sort(Comparator
                    .comparing((ShopEntry item) -> item.category == null ? "misc" : item.category)
                    .thenComparing(item -> item.id == null ? "" : item.id));
            save();
        }
        return fixed;
    }

    public synchronized int fixEconomy() {
        int fixed = 0;
        for (CategoryShopConfig categoryConfig : categoryConfigs.values()) {
            String category = ShopEntry.normalizeCategory(categoryConfig.id);
            if ("tickets".equals(category) || "crates".equals(category)) {
                continue;
            }

            int categoryFixed = 0;
            for (ShopEntry entry : categoryConfig.items) {
                if (ShopEconomyRules.apply(entry, category)) {
                    fixed++;
                    categoryFixed++;
                }
            }
            if (categoryFixed > 0) {
                saveCategoryConfig(categoryConfig);
            }
        }
        return fixed;
    }

    private Identifier itemIdentifier(ShopEntry entry) {
        try {
            ItemStack stack = ItemStackSerializer.deserializeEntry(entry, DynamicRegistryManager.EMPTY);
            if (stack.isEmpty()) {
                return entry.id == null ? null : Identifier.tryParse("minecraft:" + entry.id);
            }
            return Registries.ITEM.getId(stack.getItem());
        } catch (Exception e) {
            return entry.id == null ? null : Identifier.tryParse("minecraft:" + entry.id);
        }
    }

    public synchronized Optional<ShopEntry> get(String id) {
        return all().stream()
                .filter(item -> item.id != null && item.id.equalsIgnoreCase(id))
                .findFirst();
    }

    public synchronized Collection<ShopEntry> all() {
        ArrayList<ShopEntry> all = new ArrayList<>();
        for (CategoryShopConfig categoryConfig : categoryConfigs.values()) {
            all.addAll(categoryConfig.items);
        }
        all.addAll(config.items);
        return all;
    }

    public synchronized Optional<CategoryShopConfig> categoryConfig(String category) {
        return Optional.ofNullable(categoryConfigs.get(ShopEntry.normalizeCategory(category)));
    }

    public synchronized Collection<CategoryShopConfig> categoryConfigs() {
        return new ArrayList<>(categoryConfigs.values());
    }

    private void loadCategoryConfigs() {
        categoryConfigs = new LinkedHashMap<>();
        try {
            Files.createDirectories(categoriesDir);
            createMissingCategoryFiles();
            try (var stream = Files.list(categoriesDir)) {
                for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                    try (Reader reader = Files.newBufferedReader(file)) {
                        CategoryShopConfig categoryConfig = GSON.fromJson(reader, CategoryShopConfig.class);
                        if (categoryConfig == null || categoryConfig.id == null || categoryConfig.items == null) {
                            continue;
                        }
                        categoryConfig.id = ShopEntry.normalizeCategory(categoryConfig.id);
                        if (!ALLOWED_CATEGORY_IDS.contains(categoryConfig.id)) {
                            AdminShopMod.LOGGER.warn("Ignoring unsupported AdminShop category file {} (category={})", file.getFileName(), categoryConfig.id);
                            continue;
                        }
                        for (ShopEntry entry : categoryConfig.items) {
                            entry.category = categoryConfig.id;
                            if (entry.quantity <= 0) entry.quantity = 1;
                            if (entry.page <= 0) entry.page = 1;
                            entry.currency = Currency.normalize(entry.currency);
                        }
                        boolean repaired = repairNegativeSlots(categoryConfig, null);
                        CategoryShopConfig existing = categoryConfigs.get(categoryConfig.id);
                        if (existing == null) {
                            categoryConfigs.put(categoryConfig.id, categoryConfig);
                        } else {
                            existing.items.addAll(categoryConfig.items);
                            repaired = repairNegativeSlots(existing, null) || repaired;
                            categoryConfig = existing;
                        }
                        if (repaired) {
                            saveCategoryConfig(categoryConfig);
                        }
                    } catch (Exception e) {
                        AdminShopMod.LOGGER.warn("Failed to load AdminShop category file {}", file.getFileName(), e);
                    }
                }
            }
        } catch (IOException e) {
            AdminShopMod.LOGGER.warn("Failed to load AdminShop category configs", e);
        }
    }

    private void createMissingCategoryFiles() {
        for (String id : DEFAULT_CATEGORY_FILES) {
            Path file = categoriesDir.resolve(id + ".json");
            if (Files.exists(file)) {
                continue;
            }

            if (copyDefaultCategoryResource(id, file)) {
                continue;
            }

            CategoryShopConfig categoryConfig = emptyCategoryConfig(id);
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(categoryConfig, writer);
            } catch (IOException e) {
                AdminShopMod.LOGGER.warn("Failed to create AdminShop category file {}", file.getFileName(), e);
            }
        }
    }

    private boolean copyDefaultCategoryResource(String id, Path output) {
        String resource = "default_categories/" + id + ".json";
        try (InputStream stream = ShopManager.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                AdminShopMod.LOGGER.warn("Missing AdminShop default category resource: {}", resource);
                return false;
            }
            Files.copy(stream, output);
            AdminShopMod.LOGGER.info("Created AdminShop category config from default resource: {}", output.getFileName());
            return true;
        } catch (IOException e) {
            AdminShopMod.LOGGER.warn("Failed to copy AdminShop default category resource {}", resource, e);
            return false;
        }
    }

    private static CategoryShopConfig emptyCategoryConfig(String id) {
        CategoryShopConfig categoryConfig = new CategoryShopConfig();
        categoryConfig.id = id;
        categoryConfig.displayName = ShopCategory.displayName(id);
        categoryConfig.title = categoryConfig.displayName + " Page %page%/%maxPage%";
        categoryConfig.icon = iconFor(id);
        return categoryConfig;
    }

    private static String iconFor(String id) {
        return switch (id) {
            case "blocks" -> "minecraft:grass_block";
            case "ores" -> "minecraft:diamond_ore";
            case "foods" -> "minecraft:apple";
            case "farming" -> "minecraft:wheat";
            case "redstone" -> "minecraft:redstone";
            case "decoration" -> "minecraft:flower_pot";
            case "workstations" -> "minecraft:crafting_table";
            case "enchanting" -> "minecraft:enchanting_table";
            case "dyes" -> "minecraft:red_dye";
            case "music" -> "minecraft:music_disc_cat";
            case "potions" -> "minecraft:potion";
            case "mobs" -> "minecraft:bone";
            case "tools" -> "minecraft:iron_pickaxe";
            case "combat" -> "minecraft:iron_sword";
            case "misc" -> "minecraft:barrel";
            case "tickets" -> "minecraft:tripwire_hook";
            default -> "minecraft:barrel";
        };
    }

    public synchronized List<String> validateCategories() {
        List<String> report = new ArrayList<>();
        for (CategoryShopConfig categoryConfig : categoryConfigs.values()) {
            String id = ShopEntry.normalizeCategory(categoryConfig.id);
            if (!ALLOWED_CATEGORY_IDS.contains(id)) {
                report.add("Unsupported category: " + id);
            }
            if (categoryConfig.items == null || categoryConfig.items.isEmpty()) {
                report.add("Empty category: " + id);
            }
            if (!validItemId(categoryConfig.icon)) {
                report.add("Invalid icon for category " + id + ": " + categoryConfig.icon);
            }
            boolean repaired = repairNegativeSlots(categoryConfig, report);
            if (repaired) {
                saveCategoryConfig(categoryConfig);
            }

            Set<Integer> pages = new HashSet<>();
            for (ShopEntry entry : categoryConfig.items == null ? List.<ShopEntry>of() : categoryConfig.items) {
                if (entry.page <= 0) {
                    report.add(id + "/" + entry.id + " has invalid page " + entry.page);
                } else {
                    pages.add(entry.page);
                }
                if (RESERVED_CONTROL_SLOTS.contains(entry.slot)) {
                    report.add(id + "/" + entry.id + " uses reserved navigation slot " + entry.slot + " on page " + entry.page);
                } else if (!ALLOWED_ITEM_SLOTS.contains(entry.slot)) {
                    report.add(id + "/" + entry.id + " uses invalid item slot " + entry.slot + " on page " + entry.page);
                }
            }

            int maxPage = pages.stream().mapToInt(Integer::intValue).max().orElse(1);
            for (int page = 1; page <= maxPage; page++) {
                if (!pages.contains(page) && maxPage > 1) {
                    report.add(id + " is missing page " + page + " between 1 and " + maxPage);
                }
            }
        }
        return report;
    }

    private static boolean validItemId(String id) {
        Identifier identifier = Identifier.tryParse(id == null || id.isBlank() ? "" : id);
        return identifier != null && Registries.ITEM.containsId(identifier);
    }

    private static boolean repairNegativeSlots(CategoryShopConfig categoryConfig, List<String> report) {
        boolean changed = false;
        for (ShopEntry entry : categoryConfig.items == null ? List.<ShopEntry>of() : categoryConfig.items) {
            if (entry.page <= 0) {
                entry.page = 1;
                changed = true;
            }
            if (entry.slot >= 0) {
                continue;
            }
            Placement placement = firstAvailableSlot(categoryConfig);
            entry.page = placement.page;
            entry.slot = placement.slot;
            changed = true;
            if (report != null) {
                report.add("Fixed " + categoryConfig.id + "/" + entry.id + " from slot -1 to page " + entry.page + " slot " + entry.slot);
            }
        }
        return changed;
    }

    private static Placement firstAvailableSlot(CategoryShopConfig categoryConfig) {
        int page = 1;
        while (true) {
            for (int slot : ALLOWED_ITEM_SLOT_ORDER) {
                if (!isSlotUsed(categoryConfig, page, slot)) {
                    return new Placement(page, slot);
                }
            }
            page++;
        }
    }

    private static boolean isSlotUsed(CategoryShopConfig categoryConfig, int page, int slot) {
        if (categoryConfig.items == null) {
            return false;
        }
        for (ShopEntry entry : categoryConfig.items) {
            if (entry.page == page && entry.slot == slot) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedItemSlot(int slot) {
        return ALLOWED_ITEM_SLOTS.contains(slot);
    }

    private record Placement(int page, int slot) {
    }
}
