package com.tai.adminshop.config;

import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.util.ItemStackSerializer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DefaultShopGenerator {
    private static final Set<String> BLOCKED_ITEMS = Set.of(
            "command_block",
            "chain_command_block",
            "repeating_command_block",
            "structure_block",
            "structure_void",
            "barrier",
            "debug_stick",
            "jigsaw",
            "light",
            "knowledge_book",
            "spawner",
            "bedrock",
            "trial_spawner",
            "vault"
    );

    private DefaultShopGenerator() {
    }

    public static List<ShopEntry> generate() {
        List<ShopEntry> entries = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if (!"minecraft".equals(id.getNamespace()) || item == Items.AIR || BLOCKED_ITEMS.contains(id.getPath())) {
                continue;
            }

            ShopEntry entry = createEntry(item, id);
            if (entry != null) {
                entries.add(entry);
            }
        }

        entries.sort(java.util.Comparator
                .comparing((ShopEntry entry) -> entry.category == null ? "misc" : entry.category)
                .thenComparing(entry -> entry.id == null ? "" : entry.id));
        AdminShopMod.LOGGER.info("[AdminShop] Generated full vanilla shop with {} items.", entries.size());
        return entries;
    }

    private static ShopEntry createEntry(Item item, Identifier identifier) {
        double buyPrice = estimateBaseBuyPrice(item, identifier);
        if (buyPrice <= 0) {
            return null;
        }

        String itemId = identifier.getPath();
        String category = detectCategory(itemId);
        double sellPrice = estimateSellPrice(itemId, category, buyPrice);
        if (sellPrice >= buyPrice) {
            sellPrice = Math.max(0.0D, buyPrice * 0.35D);
        }

        ItemStack stack = new ItemStack(item, 1);
        String itemData = ItemStackSerializer.serialize(stack, DynamicRegistryManager.EMPTY);
        ShopEntry entry = new ShopEntry(itemId, category, buyPrice, sellPrice, itemData);
        entry.material = identifier.toString();
        ShopEconomyRules.apply(entry, category);
        return entry;
    }

    public static double estimateBaseBuyPrice(Item item, Identifier id) {
        String name = id.getPath().toLowerCase(Locale.ROOT);
        String category = detectCategory(name);
        String marketGroup = detectMarketGroup(name);

        if (name.contains("netherite_ingot")) return 50_000L;
        if (name.contains("netherite_scrap")) return 12_000L;
        if (name.contains("netherite")) return 35_000L;
        if (name.contains("diamond")) return materialAdjusted(name, 2_500L, 8_000L, 18_000L);
        if (name.contains("emerald")) return 3_000L;
        if (name.contains("gold")) return materialAdjusted(name, 400L, 1_200L, 3_200L);
        if (name.contains("iron")) return materialAdjusted(name, 220L, 900L, 1_800L);
        if (name.contains("copper")) return 120L;
        if (name.contains("coal") || name.contains("charcoal")) return 80L;
        if (name.contains("lapis") || name.contains("quartz") || name.contains("amethyst")) return 160L;
        if (name.contains("redstone")) return "redstone".equals(category) ? 220L : 50L;

        if ("tools".equals(category)) return toolPrice(name);
        if ("combat".equals(category)) return combatPrice(name);
        if ("foods".equals(category)) return foodPrice(name);
        if ("redstone".equals(category)) return redstonePrice(name);
        if ("wood".equals(marketGroup)) return woodPrice(name);
        if ("stone".equals(marketGroup)) return stonePrice(name);
        if ("sand_glass".equals(marketGroup)) return name.contains("glass") ? 35L : 18L;
        if ("clay_brick".equals(marketGroup)) return name.contains("brick") ? 80L : 50L;
        if ("wool".equals(marketGroup)) return name.contains("bed") ? 180L : name.contains("carpet") ? 24L : 45L;
        if ("farming".equals(category)) return farmingPrice(name);
        if ("mobs".equals(category)) return mobDropPrice(name);

        if (name.contains("bucket")) return name.equals("bucket") ? 700L : 1_000L;
        if (name.contains("saddle")) return 3_000L;
        if (name.contains("name_tag")) return 2_500L;
        if (name.contains("elytra")) return 100_000L;
        if (name.contains("totem")) return 25_000L;
        if (name.contains("enchanted_book")) return 8_000L;
        if (name.contains("spawn_egg")) return 10_000L;
        if (name.contains("music_disc")) return 2_000L;
        if (name.contains("shulker_box")) return 8_000L;

        return 80L;
    }

    private static long materialAdjusted(String name, long baseMaterial, long toolPrice, long armorPrice) {
        if (isArmor(name)) return armorPrice;
        if (isTool(name) || name.contains("sword")) return toolPrice;
        if (name.contains("block")) return baseMaterial * 9L;
        if (name.contains("nugget")) return Math.max(5L, baseMaterial / 9L);
        if (name.contains("raw")) return Math.max(10L, Math.round(baseMaterial * 0.8D));
        return baseMaterial;
    }

    private static long toolPrice(String name) {
        if (name.contains("diamond")) return 8_000L;
        if (name.contains("iron")) return 900L;
        if (name.contains("gold")) return 1_200L;
        if (name.contains("stone")) return 140L;
        if (name.contains("wooden")) return 80L;
        if (name.contains("shears")) return 450L;
        if (name.contains("fishing_rod")) return 250L;
        if (name.contains("flint_and_steel")) return 280L;
        if (name.contains("brush")) return 250L;
        return 500L;
    }

    private static long combatPrice(String name) {
        if (name.contains("diamond")) return isArmor(name) ? 16_000L : 5_500L;
        if (name.contains("iron")) return isArmor(name) ? 1_400L : 700L;
        if (name.contains("gold")) return isArmor(name) ? 2_000L : 1_000L;
        if (name.contains("leather")) return 300L;
        if (name.contains("chainmail")) return 1_800L;
        if (name.contains("bow")) return name.contains("crossbow") ? 900L : 600L;
        if (name.contains("trident")) return 12_000L;
        if (name.contains("shield")) return 850L;
        if (name.contains("arrow")) return 20L;
        return 800L;
    }

    private static long foodPrice(String name) {
        if (name.contains("golden")) return 500L;
        if (name.contains("cake")) return 450L;
        if (name.contains("stew") || name.contains("soup")) return 120L;
        if (name.contains("cooked")) return 140L;
        if (name.contains("beef") || name.contains("porkchop") || name.contains("mutton") || name.contains("chicken") || name.contains("cod") || name.contains("salmon")) return 90L;
        if (name.contains("apple") || name.contains("bread") || name.contains("pie") || name.contains("cookie")) return 90L;
        return 45L;
    }

    private static long redstonePrice(String name) {
        if (name.contains("hopper")) return 900L;
        if (name.contains("observer") || name.contains("sticky_piston")) return 500L;
        if (name.contains("piston") || name.contains("dispenser")) return 380L;
        if (name.contains("comparator")) return 260L;
        if (name.contains("repeater") || name.contains("lamp")) return 180L;
        if (name.contains("rail")) return 120L;
        return 60L;
    }

    private static long woodPrice(String name) {
        if (name.endsWith("_log") || name.endsWith("_wood") || name.endsWith("_stem")) return 70L;
        if (name.endsWith("_planks")) return 18L;
        if (name.endsWith("_sapling")) return 60L;
        if (name.endsWith("_leaves")) return 20L;
        if (name.equals("stick")) return 5L;
        return 35L;
    }

    private static long stonePrice(String name) {
        if (name.contains("obsidian")) return 600L;
        if (name.contains("basalt") || name.contains("tuff") || name.contains("calcite")) return 30L;
        if (name.contains("deepslate") || name.contains("blackstone")) return 24L;
        if (name.contains("cobblestone") || name.contains("netherrack")) return 12L;
        return 20L;
    }

    private static long farmingPrice(String name) {
        if (name.contains("bone_meal")) return 35L;
        if (name.contains("sapling")) return 60L;
        if (name.contains("seed")) return 25L;
        return 45L;
    }

    private static long mobDropPrice(String name) {
        if (name.contains("ghast_tear")) return 1_200L;
        if (name.contains("blaze_rod")) return 600L;
        if (name.contains("ender_pearl")) return 500L;
        if (name.contains("slime_ball")) return 220L;
        if (name.contains("gunpowder")) return 180L;
        return 70L;
    }

    private static double estimateSellPrice(String itemId, String category, double buyPrice) {
        if (itemId.contains("water_bucket") || itemId.contains("lava_bucket") || itemId.contains("saddle")
                || itemId.contains("name_tag") || itemId.contains("enchanted_book") || itemId.contains("spawn_egg")
                || itemId.contains("music_disc") || itemId.contains("elytra") || itemId.contains("totem")) {
            return 0.0D;
        }
        if ("tools".equals(category) || "combat".equals(category)) {
            return Math.max(0.0D, buyPrice * 0.20D);
        }
        return Math.max(0.01D, buyPrice * 0.33D);
    }

    public static String detectPriceGroup(String itemId, String category) {
        return ShopEconomyRules.detectPriceGroup(itemId, category);
    }

    public static String detectCategory(String itemId) {
        String marketGroup = detectMarketGroup(itemId);
        return switch (marketGroup) {
            case "stone", "wood", "sand_glass", "clay_brick", "wool", "building" -> "blocks";
            case "ores" -> "ores";
            case "farming" -> "farming";
            case "mob_drops" -> "mobs";
            case "redstone" -> "redstone";
            default -> {
                String name = itemId.toLowerCase(Locale.ROOT);
                if (isFoodName(name)) yield "foods";
                yield "misc";
            }
        };
    }

    private static String detectMarketGroup(String itemId) {
        String name = itemId.toLowerCase(Locale.ROOT);
        if (name.endsWith("_log") || name.endsWith("_planks") || name.contains("_wood") || name.endsWith("_sapling") || name.endsWith("_leaves") || name.equals("stick")) return "wood";
        if (containsAny(name, "stone", "cobblestone", "deepslate", "blackstone", "netherrack", "basalt", "tuff", "calcite", "dripstone")) return "stone";
        if (containsAny(name, "sand", "glass")) return "sand_glass";
        if (containsAny(name, "clay", "brick", "terracotta")) return "clay_brick";
        if (containsAny(name, "wool", "carpet", "bed")) return "wool";
        if (containsAny(name, "ore", "raw_", "ingot", "nugget", "gem", "diamond", "emerald", "coal", "lapis", "redstone", "quartz", "amethyst", "netherite")) return "ores";
        if (containsAny(name, "seed", "wheat", "carrot", "potato", "beetroot", "sugar_cane", "cactus", "bamboo", "kelp", "cocoa", "nether_wart")) return "farming";
        if (containsAny(name, "bone", "string", "spider_eye", "rotten_flesh", "gunpowder", "ender_pearl", "slime_ball", "leather", "feather", "blaze_rod", "ghast_tear", "rabbit_foot", "phantom_membrane")) return "mob_drops";
        if (containsAny(name, "redstone", "repeater", "comparator", "piston", "observer", "hopper", "dispenser", "dropper", "lever", "button", "pressure_plate", "rail")) return "redstone";
        return "misc";
    }

    private static boolean isFoodName(String name) {
        return containsAny(name, "apple", "bread", "cooked", "beef", "porkchop", "mutton", "chicken", "cod", "salmon",
                "rabbit", "stew", "soup", "pie", "cookie", "cake", "melon_slice", "berries", "carrot", "potato");
    }

    private static boolean isTool(String name) {
        return containsAny(name, "pickaxe", "axe", "shovel", "hoe");
    }

    private static boolean isArmor(String name) {
        return containsAny(name, "helmet", "chestplate", "leggings", "boots");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
