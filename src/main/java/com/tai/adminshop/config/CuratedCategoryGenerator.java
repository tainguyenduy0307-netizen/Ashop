package com.tai.adminshop.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tai.adminshop.AdminShopMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class CuratedCategoryGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private CuratedCategoryGenerator() {
    }

    public static int generate(boolean force) throws IOException {
        Path categoriesDir = FabricLoader.getInstance().getConfigDir().resolve("adminshop").resolve("categories");
        Files.createDirectories(categoriesDir);
        if (force) {
            backupCategories(categoriesDir);
            cleanupCategories(categoriesDir);
        }

        int total = 0;
        for (Spec spec : specs()) {
            Path file = categoriesDir.resolve(spec.id + ".json");
            if (!force && Files.exists(file) && fileHasItems(file)) {
                continue;
            }
            if ("tickets".equals(spec.id) && Files.exists(file) && fileHasItems(file)) {
                continue;
            }

            CategoryShopConfig config = categoryConfig(spec);
            total += config.items.size();
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(config, writer);
            }
        }
        return total;
    }

    private static CategoryShopConfig categoryConfig(Spec spec) {
        CategoryShopConfig config = new CategoryShopConfig();
        config.id = spec.id;
        config.displayName = ShopCategory.displayName(spec.id);
        config.title = config.displayName + " Page %page%/%maxPage%";
        config.icon = spec.icon;
        config.fillItem = "minecraft:gray_stained_glass_pane";

        int index = 0;
        for (String itemId : spec.items) {
            Identifier material = Identifier.ofVanilla(itemId);
            if (!Registries.ITEM.containsId(material)) {
                AdminShopMod.LOGGER.warn("Skipping missing curated shop item: {}", material);
                continue;
            }

            int page = index / ITEM_SLOTS.length + 1;
            int slot = ITEM_SLOTS[index % ITEM_SLOTS.length];
            Price price = price(itemId, spec.id);
            if (price.buy <= 0 || price.sell >= price.buy) {
                price = new Price(Math.max(1.0D, price.buy), Math.max(0.0D, price.buy * 0.25D));
            }

            ShopEntry entry = new ShopEntry(itemId, spec.id, price.buy, price.sell, null);
            entry.material = material.toString();
            entry.quantity = 1;
            entry.slot = slot;
            entry.page = page;
            ShopEconomyRules.apply(entry, spec.id);
            config.items.add(entry);
            index++;
        }
        return config;
    }

    private static Price price(String itemId, String category) {
        double buy = switch (itemId) {
            case "diamond" -> 50_000;
            case "emerald" -> 40_000;
            case "netherite_scrap" -> 160_000;
            case "netherite_ingot" -> 600_000;
            case "iron_ingot" -> 6_400;
            case "gold_ingot" -> 9_600;
            case "copper_ingot" -> 2_400;
            case "coal", "charcoal" -> 2_000;
            case "lapis_lazuli", "redstone", "quartz", "amethyst_shard" -> 2_400;
            case "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log", "mangrove_log", "cherry_log" -> 320;
            case "stone", "cobblestone", "dirt", "gravel", "netherrack" -> 128;
            case "sand", "red_sand", "glass" -> 192;
            case "obsidian" -> 3_200;
            default -> defaultBuy(category, itemId);
        };

        if (Set.of("water_bucket", "lava_bucket", "saddle", "name_tag", "music_disc_13", "music_disc_cat",
                "music_disc_blocks", "music_disc_chirp", "music_disc_far", "music_disc_mall", "music_disc_mellohi",
                "music_disc_stal", "music_disc_strad", "music_disc_ward", "music_disc_11", "music_disc_wait",
                "music_disc_pigstep", "music_disc_otherside", "music_disc_5", "music_disc_relic").contains(itemId)) {
            return new Price(buy, 0.0D);
        }
        double rate = switch (category) {
            case "ores" -> 0.30D;
            case "tools", "combat" -> 0.20D;
            default -> 0.25D;
        };
        return new Price(buy, Math.max(0.01D, buy * rate));
    }

    private static long defaultBuy(String category, String itemId) {
        return switch (category) {
            case "blocks" -> itemId.endsWith("_wool") ? 256 : 384;
            case "ores" -> 4_000;
            case "foods" -> itemId.contains("golden") ? 2_000 : 640;
            case "farming" -> 384;
            case "redstone" -> itemId.contains("hopper") || itemId.contains("observer") ? 3_200 : 1_200;
            case "tools" -> itemId.contains("diamond") ? 30_000 : itemId.contains("iron") ? 6_000 : 1_000;
            case "combat" -> itemId.contains("diamond") ? 40_000 : itemId.contains("iron") ? 8_000 : 2_000;
            case "decoration" -> 1_000;
            case "workstations" -> itemId.contains("enchanting") ? 20_000 : itemId.contains("anvil") ? 12_000 : 1_500;
            case "enchanting" -> itemId.contains("experience_bottle") ? 2_500 : 1_200;
            case "dyes" -> 320;
            case "music" -> 8_000;
            case "potions" -> 800;
            default -> 1_000;
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

    private static void backupCategories(Path categoriesDir) throws IOException {
        Path backupDir = categoriesDir.getParent().resolve("backups").resolve("categories-" + LocalDateTime.now().format(BACKUP_FORMAT));
        Files.createDirectories(backupDir);
        try (var stream = Files.list(categoriesDir)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                Files.copy(file, backupDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static List<Spec> specs() {
        List<Spec> specs = new ArrayList<>();
        specs.add(new Spec("blocks", "minecraft:grass_block", List.of("stone", "cobblestone", "deepslate", "cobbled_deepslate", "dirt", "grass_block", "sand", "red_sand", "gravel", "glass", "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log", "mangrove_log", "cherry_log", "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks", "mangrove_planks", "cherry_planks", "bricks", "clay", "terracotta", "blackstone", "netherrack", "soul_sand", "obsidian", "white_wool", "orange_wool", "magenta_wool", "light_blue_wool", "yellow_wool", "lime_wool", "pink_wool", "gray_wool", "light_gray_wool", "cyan_wool", "purple_wool", "blue_wool", "brown_wool", "green_wool", "red_wool", "black_wool")));
        specs.add(new Spec("ores", "minecraft:diamond_ore", List.of("coal", "charcoal", "raw_iron", "iron_ingot", "raw_copper", "copper_ingot", "raw_gold", "gold_ingot", "redstone", "lapis_lazuli", "diamond", "emerald", "quartz", "amethyst_shard", "netherite_scrap", "netherite_ingot")));
        specs.add(new Spec("foods", "minecraft:apple", List.of("apple", "bread", "cooked_beef", "cooked_porkchop", "cooked_chicken", "cooked_mutton", "cooked_cod", "cooked_salmon", "carrot", "potato", "baked_potato", "golden_carrot", "pumpkin_pie", "melon_slice", "sweet_berries")));
        specs.add(new Spec("farming", "minecraft:wheat", List.of("wheat_seeds", "wheat", "beetroot_seeds", "beetroot", "melon_seeds", "pumpkin_seeds", "sugar_cane", "cactus", "bamboo", "oak_sapling", "spruce_sapling", "birch_sapling", "bone_meal", "kelp", "nether_wart", "cocoa_beans")));
        specs.add(new Spec("redstone", "minecraft:redstone", List.of("redstone", "repeater", "comparator", "piston", "sticky_piston", "observer", "hopper", "dispenser", "dropper", "lever", "redstone_torch", "redstone_lamp", "daylight_detector", "tripwire_hook", "rail", "powered_rail", "detector_rail", "activator_rail")));
        specs.add(new Spec("decoration", "minecraft:flower_pot", List.of("painting", "item_frame", "glow_item_frame", "armor_stand", "flower_pot", "lantern", "soul_lantern", "chain", "white_banner", "red_banner", "blue_banner", "black_banner", "oak_sign", "spruce_sign", "birch_sign", "cherry_sign")));
        specs.add(new Spec("workstations", "minecraft:crafting_table", List.of("crafting_table", "furnace", "blast_furnace", "smoker", "cartography_table", "fletching_table", "smithing_table", "stonecutter", "grindstone", "loom", "lectern", "composter", "anvil", "enchanting_table", "brewing_stand")));
        specs.add(new Spec("enchanting", "minecraft:enchanting_table", List.of()));
        specs.add(new Spec("dyes", "minecraft:red_dye", List.of()));
        specs.add(new Spec("music", "minecraft:music_disc_cat", List.of("music_disc_13", "music_disc_cat", "music_disc_blocks", "music_disc_chirp", "music_disc_far", "music_disc_mall", "music_disc_mellohi", "music_disc_stal", "music_disc_strad", "music_disc_ward", "music_disc_11", "music_disc_wait", "music_disc_pigstep", "music_disc_otherside", "music_disc_5", "music_disc_relic")));
        specs.add(new Spec("potions", "minecraft:potion", List.of()));
        specs.add(new Spec("mobs", "minecraft:bone", List.of()));
        specs.add(new Spec("misc", "minecraft:barrel", List.of()));
        specs.add(new Spec("tickets", "minecraft:tripwire_hook", List.of()));
        return specs;
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

    private record Spec(String id, String icon, List<String> items) {
    }

    private record Price(double buy, double sell) {
    }
}
