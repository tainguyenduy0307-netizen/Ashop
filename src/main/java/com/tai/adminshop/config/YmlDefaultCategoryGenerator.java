package com.tai.adminshop.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tai.adminshop.AdminShopMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class YmlDefaultCategoryGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final int[] DEFAULT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final Set<String> BLOCKED_ITEMS = Set.of(
            "command_block", "chain_command_block", "repeating_command_block", "structure_block", "structure_void",
            "barrier", "debug_stick", "jigsaw", "light", "knowledge_book", "bedrock", "spawner",
            "trial_spawner", "vault"
    );
    private static final Set<String> EMPTY_ITEM_CATEGORIES = Set.of("potions", "enchanting", "dyes", "misc");

    private YmlDefaultCategoryGenerator() {
    }

    public static int generate(boolean force) throws IOException {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("adminshop");
        Path categoriesDir = configDir.resolve("categories");
        Files.createDirectories(categoriesDir);
        if (force) {
            backupCategories(categoriesDir);
            cleanupCategories(categoriesDir);
        }

        Map<String, CategoryShopConfig> generated = new LinkedHashMap<>();
        for (FileMapping mapping : mappings()) {
            CategoryShopConfig config = readCategory(mapping, configDir);
            if (config == null) {
                AdminShopMod.LOGGER.warn("Skipping missing default shop YAML: {}", mapping.filename);
                continue;
            }
            if ("tickets".equals(mapping.category) && Files.exists(categoriesDir.resolve(mapping.category + ".json")) && fileHasItems(categoriesDir.resolve(mapping.category + ".json"))) {
                continue;
            }
            CategoryShopConfig existing = generated.get(mapping.category);
            if (existing == null) {
                generated.put(mapping.category, config);
            } else {
                appendItems(existing.items, config.items);
            }
        }

        int total = 0;
        for (CategoryShopConfig config : generated.values()) {
            total += config.items.size();
            Path output = categoriesDir.resolve(config.id + ".json");
            try (Writer writer = Files.newBufferedWriter(output)) {
                GSON.toJson(config, writer);
            }
        }
        ensureTickets(categoriesDir);
        return total;
    }

    private static void appendItems(List<ShopEntry> target, List<ShopEntry> source) {
        for (ShopEntry entry : source) {
            Placement placement = placement(target, entry.page, entry.slot);
            entry.page = placement.page;
            entry.slot = placement.slot;
            target.add(entry);
        }
    }

    private static CategoryShopConfig readCategory(FileMapping mapping, Path configDir) throws IOException {
        Object loaded = loadYaml(mapping.filename, configDir);
        if (!(loaded instanceof Map<?, ?> rootRaw)) {
            return null;
        }

        Map<String, Object> root = castMap(rootRaw);
        Map<String, Object> shopRoot = unwrapShopRoot(root);
        CategoryShopConfig config = new CategoryShopConfig();
        config.id = mapping.category;
        config.displayName = ShopCategory.displayName(mapping.category);
        config.title = title(shopRoot, config.displayName);
        config.size = number(shopRoot.get("size"), 54);
        config.fillItem = materialIdFromFill(shopRoot.get("fillItem"), "minecraft:gray_stained_glass_pane");
        config.icon = iconFor(mapping.category);
        parseButtons(config, shopRoot.get("buttons"));

        if (EMPTY_ITEM_CATEGORIES.contains(mapping.category)) {
            config.items = new ArrayList<>();
            return config;
        }

        List<ShopEntry> items = new ArrayList<>();
        Object pages = root.get("pages");
        if (pages instanceof Map<?, ?> pagesMap) {
            int pageNumber = 1;
            for (Object pageValue : pagesMap.values()) {
                if (pageValue instanceof Map<?, ?> pageMap) {
                    parseItems(items, mapping.category, castMap((Map<?, ?>) pageMap).get("items"), pageNumber);
                    pageNumber++;
                }
            }
        } else {
            parseItems(items, mapping.category, shopRoot.get("items"), 1);
        }
        config.items = items;
        return config;
    }

    private static Object loadYaml(String filename, Path configDir) throws IOException {
        Path importFile = configDir.resolve("import").resolve(filename);
        Yaml yaml = new Yaml();
        if (Files.exists(importFile)) {
            try (Reader reader = Files.newBufferedReader(importFile)) {
                return yaml.load(reader);
            }
        }

        String resource = "default_shop_yml/" + filename;
        try (InputStream stream = YmlDefaultCategoryGenerator.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            return yaml.load(stream);
        }
    }

    private static void parseItems(List<ShopEntry> entries, String category, Object itemsObject, int page) {
        if (!(itemsObject instanceof Map<?, ?> items)) {
            return;
        }

        int fallbackIndex = 0;
        for (Map.Entry<?, ?> rawEntry : items.entrySet()) {
            if (!(rawEntry.getValue() instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> itemMap = castMap(rawMap);
            if (itemMap.containsKey("buy-commands") || itemMap.containsKey("sell-commands")) {
                continue;
            }
            Map<String, Object> nestedItem = itemMap.get("item") instanceof Map<?, ?> nested ? castMap(nested) : itemMap;
            Identifier material = materialId(string(nestedItem.get("material"), string(itemMap.get("material"), "")));
            if (material == null || isBlocked(material, category)) {
                if (material != null) {
                    AdminShopMod.LOGGER.warn("Skipping blocked or unavailable shop material: {}", material);
                }
                continue;
            }

            double buyPrice = price(itemMap.get("buyPrice"), itemMap.get("buy"));
            double sellPrice = price(itemMap.get("sellPrice"), itemMap.get("sell"));
            if (buyPrice < 0) {
                buyPrice = 0;
            }
            if (sellPrice < 0) {
                sellPrice = 0;
            }
            if (buyPrice > 0 && sellPrice >= buyPrice) {
                sellPrice = Math.max(0.0D, buyPrice * 0.4D);
            }

            ShopEntry entry = new ShopEntry(material.getPath(), category, buyPrice, sellPrice, null);
            entry.material = material.toString();
            entry.quantity = Math.max(1, number(nestedItem.get("quantity"), number(itemMap.get("quantity"), 1)));
            Placement placement = placement(entries, page, number(itemMap.get("slot"), fallbackSlot(rawEntry.getKey(), fallbackIndex)));
            entry.slot = placement.slot;
            entry.page = placement.page;
            entry.name = string(nestedItem.get("name"), string(itemMap.get("name"), null));
            entry.lore = stringList(nestedItem.get("lore") == null ? itemMap.get("lore") : nestedItem.get("lore"));
            ShopEconomyRules.apply(entry, category);
            entries.add(entry);
            fallbackIndex++;
        }
    }

    private static List<FileMapping> mappings() {
        return List.of(
                new FileMapping("Blocks.yml", "blocks"),
                new FileMapping("Ores.yml", "ores"),
                new FileMapping("Food.yml", "foods"),
                new FileMapping("Farming.yml", "farming"),
                new FileMapping("Redstone.yml", "redstone"),
                new FileMapping("Decoration.yml", "decoration"),
                new FileMapping("Dyes.yml", "dyes"),
                new FileMapping("Enchanting.yml", "enchanting"),
                new FileMapping("Mobs.yml", "mobs"),
                new FileMapping("Music.yml", "music"),
                new FileMapping("Potions.yml", "potions"),
                new FileMapping("Workstations.yml", "workstations"),
                new FileMapping("Miscellaneous.yml", "misc"),
                new FileMapping("Z_EverythingElse.yml", "misc")
        );
    }

    private static Map<String, Object> unwrapShopRoot(Map<String, Object> root) {
        if (root.containsKey("items") || root.containsKey("pages")) {
            return root;
        }
        for (Object value : root.values()) {
            if (value instanceof Map<?, ?> map && (map.containsKey("items") || map.containsKey("buttons"))) {
                return castMap(map);
            }
        }
        return root;
    }

    private static void parseButtons(CategoryShopConfig config, Object buttons) {
        if (!(buttons instanceof Map<?, ?> buttonMap)) {
            return;
        }
        config.buttons.back = buttonSlot(castMapObject(buttonMap.get("goBack")), config.buttons.back);
        config.buttons.previousPage = buttonSlot(castMapObject(buttonMap.get("previousPage")), config.buttons.previousPage);
        config.buttons.nextPage = buttonSlot(castMapObject(buttonMap.get("nextPage")), config.buttons.nextPage);
    }

    private static int buttonSlot(Map<String, Object> map, int fallback) {
        return map == null ? fallback : number(map.get("slot"), fallback);
    }

    private static boolean isBlocked(Identifier material, String category) {
        String path = material.getPath();
        if (!"minecraft".equals(material.getNamespace()) || BLOCKED_ITEMS.contains(path)) {
            return true;
        }
        if ("ores".equals(category) && "copper_ore".equals(path)) {
            return true;
        }
        return path.endsWith("_spawn_egg") && !"spawners".equals(category);
    }

    private static Identifier materialId(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        String normalized = materialName.toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        Identifier id = Identifier.tryParse(normalized);
        return id != null && Registries.ITEM.containsId(id) ? id : null;
    }

    private static String materialIdFromFill(Object value, String fallback) {
        if (value instanceof Map<?, ?> map) {
            Identifier id = materialId(string(map.get("material"), ""));
            return id == null ? fallback : id.toString();
        }
        Identifier id = materialId(string(value, ""));
        return id == null ? fallback : id.toString();
    }

    private static String title(Map<String, Object> root, String fallbackName) {
        String name = string(root.get("name"), fallbackName + " Page %page%/%maxPage%");
        return stripColorCodes(name).replace("(page %page%)", "Page %page%/%maxPage%");
    }

    private static String stripColorCodes(String value) {
        return value == null ? "" : value.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    private static double price(Object primary, Object fallback) {
        Object value = primary == null ? fallback : primary;
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0.0D;
        }
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int fallbackSlot(Object key, int index) {
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(key).replace("'", "")) - 1);
        } catch (Exception e) {
            return DEFAULT_SLOTS[index % DEFAULT_SLOTS.length];
        }
    }

    private static Placement placement(List<ShopEntry> entries, int preferredPage, int requestedSlot) {
        int page = Math.max(1, preferredPage);
        if (isAllowedItemSlot(requestedSlot) && !isUsed(entries, page, requestedSlot)) {
            return new Placement(page, requestedSlot);
        }

        while (true) {
            for (int slot : DEFAULT_SLOTS) {
                if (!isUsed(entries, page, slot)) {
                    return new Placement(page, slot);
                }
            }
            page++;
        }
    }

    private static boolean isAllowedItemSlot(int slot) {
        for (int allowed : DEFAULT_SLOTS) {
            if (allowed == slot) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUsed(List<ShopEntry> entries, int page, int slot) {
        for (ShopEntry entry : entries) {
            if (entry.page == page && entry.slot == slot) {
                return true;
            }
        }
        return false;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Map<String, Object> castMapObject(Object value) {
        return value instanceof Map<?, ?> map ? castMap(map) : null;
    }

    private static String iconFor(String category) {
        return switch (category) {
            case "blocks" -> "minecraft:grass_block";
            case "ores" -> "minecraft:diamond_ore";
            case "foods" -> "minecraft:apple";
            case "farming" -> "minecraft:wheat";
            case "redstone" -> "minecraft:redstone";
            case "decoration" -> "minecraft:flower_pot";
            case "dyes" -> "minecraft:red_dye";
            case "enchanting" -> "minecraft:enchanting_table";
            case "mobs" -> "minecraft:bone";
            case "music" -> "minecraft:music_disc_cat";
            case "potions" -> "minecraft:potion";
            case "workstations" -> "minecraft:crafting_table";
            case "misc" -> "minecraft:barrel";
            case "tickets" -> "minecraft:tripwire_hook";
            default -> "minecraft:barrel";
        };
    }

    private static boolean fileHasItems(Path file) {
        try {
            CategoryShopConfig config = GSON.fromJson(Files.readString(file), CategoryShopConfig.class);
            return config != null && config.items != null && !config.items.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static void ensureTickets(Path categoriesDir) throws IOException {
        Path ticketsFile = categoriesDir.resolve("tickets.json");
        if (Files.exists(ticketsFile)) {
            return;
        }
        CategoryShopConfig config = new CategoryShopConfig();
        config.id = "tickets";
        config.displayName = "Tickets";
        config.title = "Tickets Page %page%/%maxPage%";
        config.icon = "minecraft:tripwire_hook";
        try (Writer writer = Files.newBufferedWriter(ticketsFile)) {
            GSON.toJson(config, writer);
        }
    }

    private static void backupCategories(Path categoriesDir) throws IOException {
        Path backupDir = categoriesDir.getParent().resolve("backups").resolve("categories-" + LocalDateTime.now().format(BACKUP_FORMAT));
        Files.createDirectories(backupDir);
        if (!Files.isDirectory(categoriesDir)) {
            return;
        }
        try (var stream = Files.list(categoriesDir)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                Files.copy(file, backupDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void cleanupCategories(Path categoriesDir) throws IOException {
        try (var stream = Files.list(categoriesDir)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                String id = name.substring(0, name.length() - ".json".length());
                if ("tickets".equals(id) && fileHasItems(file)) {
                    continue;
                }
                Files.deleteIfExists(file);
            }
        }
    }

    private record FileMapping(String filename, String category) {
    }

    private record Placement(int page, int slot) {
    }
}
