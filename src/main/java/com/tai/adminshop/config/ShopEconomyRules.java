package com.tai.adminshop.config;

import java.util.Locale;

public final class ShopEconomyRules {
    private ShopEconomyRules() {
    }

    public static boolean apply(ShopEntry entry, String categoryId) {
        if (entry == null) {
            return false;
        }

        String category = ShopEntry.normalizeCategory(categoryId == null ? entry.category : categoryId);
        if ("tickets".equals(category) || "crates".equals(category)) {
            return false;
        }

        Snapshot before = Snapshot.of(entry);
        String materialId = materialId(entry);
        String itemId = path(materialId);
        entry.category = category;
        entry.priceGroup = detectPriceGroup(materialId, category);

        Double configuredSellPrice = configuredSellPrice(itemId, category);
        if (configuredSellPrice != null) {
            entry.sellPrice = configuredSellPrice;
            if (entry.sellPrice > 0) {
                entry.buyPrice = entry.sellPrice * 50.0D;
            } else {
                entry.buyPrice = Math.max(1.0D, entry.buyPrice);
            }
        } else if (entry.sellPrice > 0) {
            entry.buyPrice = entry.sellPrice * 50.0D;
        }

        if (entry.sellPrice > 0) {
            entry.dynamicPricing = true;
            entry.minMultiplier = 0.7D;
            entry.maxMultiplier = 1.3D;
        } else {
            entry.dynamicPricing = false;
            entry.minMultiplier = 1.0D;
            entry.maxMultiplier = 1.0D;
        }

        return !before.equals(Snapshot.of(entry));
    }

    private static Double configuredSellPrice(String id, String category) {
        if (isBambooChain(id) || isFarmMachineItem(id)) return 0.0D;
        if ("mobs".equals(category) || "mob_drops".equals(category)) {
            Double mobPrice = mobSellPrice(id);
            if (mobPrice != null) return mobPrice;
        }
        if ("bookshelf".equals(id)) return 15.0D;
        if ("chiseled_bookshelf".equals(id)) return 3.0D;
        Double copperPrice = copperSellPrice(id);
        if (copperPrice != null) return copperPrice;
        Double orePrice = oreSellPrice(id);
        if (orePrice != null) return orePrice;
        Double stonePrice = stoneSellPrice(id);
        if (stonePrice != null) return stonePrice;
        Double woodPrice = woodSellPrice(id);
        if (woodPrice != null) return woodPrice;
        Double sandGlassPrice = sandGlassSellPrice(id);
        if (sandGlassPrice != null) return sandGlassPrice;
        Double clayBrickPrice = clayBrickSellPrice(id);
        if (clayBrickPrice != null) return clayBrickPrice;
        Double buildingPrice = buildingSellPrice(id);
        if (buildingPrice != null) return buildingPrice;

        return switch (id) {
            case "tnt", "water_bucket", "lava_bucket" -> 0.0D;
            case "sand", "red_sand", "gravel" -> 1.0D;
            case "clay", "brick" -> 2.0D;
            default -> null;
        };
    }

    public static String detectPriceGroup(String materialId, String categoryId) {
        String id = path(materialId);
        String category = ShopEntry.normalizeCategory(categoryId);

        if (copperSellPrice(id) != null) return "copper";
        if (oreSellPrice(id) != null) return "ores";
        if (stoneSellPrice(id) != null) return "stone";
        if (woodSellPrice(id) != null) return "wood";
        if (id.endsWith("_concrete") || id.endsWith("_concrete_powder")) return "sand_glass";
        if (sandGlassSellPrice(id) != null || "sand".equals(id) || "red_sand".equals(id) || "gravel".equals(id)) return "sand_glass";
        if (clayBrickSellPrice(id) != null || "clay".equals(id) || "brick".equals(id)) return "clay_brick";
        if (isWood(id)) return "wood";
        if (isWool(id)) return "wool";
        if (isOreGroup(id)) return "ores";
        if (isCopper(id)) return "copper";
        if (isStone(id)) return "stone";
        if (isBuilding(id)) return "building";
        return category;
    }

    private static Double oreSellPrice(String id) {
        return switch (id) {
            case "coal", "charcoal", "redstone", "lapis_lazuli", "copper_ingot" -> 1.0D;
            case "raw_copper" -> 0.75D;
            case "iron_ingot" -> 3.0D;
            case "raw_iron" -> 2.25D;
            case "gold_ingot" -> 5.0D;
            case "raw_gold" -> 3.75D;
            case "diamond" -> 25.0D;
            case "emerald" -> 30.0D;
            case "quartz", "amethyst_shard" -> 2.0D;
            case "netherite_scrap" -> 400.0D;
            case "netherite_ingot" -> 1600.0D;
            case "ancient_debris" -> 400.0D;
            case "coal_block", "redstone_block", "lapis_block" -> 9.0D;
            case "raw_copper_block" -> 6.75D;
            case "iron_block" -> 27.0D;
            case "raw_iron_block" -> 20.25D;
            case "gold_block" -> 45.0D;
            case "raw_gold_block" -> 33.75D;
            case "diamond_block" -> 225.0D;
            case "emerald_block" -> 270.0D;
            case "netherite_block" -> 14_400.0D;
            case "quartz_block", "smooth_quartz", "quartz_bricks", "quartz_pillar", "chiseled_quartz_block" -> 8.0D;
            case "quartz_slab", "smooth_quartz_slab" -> 4.0D;
            case "quartz_stairs", "smooth_quartz_stairs" -> 12.0D;
            default -> null;
        };
    }

    private static Double stoneSellPrice(String id) {
        Double base = stoneBasePrice(id);
        if (base != null) {
            return base;
        }

        base = stoneVariantBasePrice(id);
        if (base == null) {
            return null;
        }
        if (id.endsWith("_slab")) return base * 0.5D;
        if (id.endsWith("_stairs")) return base * 1.5D;
        if (id.endsWith("_wall")) return base;
        if (id.contains("polished") || id.contains("chiseled") || id.contains("bricks") || id.contains("brick") || id.contains("tiles") || id.contains("tile")) {
            return base;
        }
        return null;
    }

    private static Double stoneBasePrice(String id) {
        return switch (id) {
            case "stone", "cobblestone", "andesite", "granite", "diorite", "tuff", "netherrack" -> 1.0D;
            case "deepslate", "cobbled_deepslate", "blackstone", "basalt", "calcite", "dripstone_block", "end_stone" -> 1.5D;
            default -> null;
        };
    }

    private static Double stoneVariantBasePrice(String id) {
        if (id.contains("deepslate") || id.contains("blackstone") || id.contains("basalt") || id.contains("end_stone")) {
            return 1.5D;
        }
        if (id.contains("stone") || id.contains("cobblestone") || id.contains("andesite") || id.contains("granite")
                || id.contains("diorite") || id.contains("tuff") || id.contains("netherrack")) {
            return 1.0D;
        }
        return null;
    }

    private static Double woodSellPrice(String id) {
        if (isLog(id)) return 3.0D;
        if (id.endsWith("_planks")) return 1.0D;
        if ("bamboo_block".equals(id)) return 3.0D;
        if ("bamboo_planks".equals(id) || "bamboo_mosaic".equals(id)) return 1.0D;
        if ("bamboo_slab".equals(id) || "bamboo_mosaic_slab".equals(id)) return 0.5D;
        if ("bamboo_stairs".equals(id) || "bamboo_mosaic_stairs".equals(id)) return 1.5D;
        if (!isWoodFamily(id)) return null;
        if (id.endsWith("_slab")) return 0.5D;
        if (id.endsWith("_stairs")) return 1.5D;
        if (id.endsWith("_fence")) return 1.5D;
        if (id.endsWith("_fence_gate")) return 3.0D;
        if (id.endsWith("_door")) return 3.0D;
        if (id.endsWith("_trapdoor")) return 3.0D;
        if (id.endsWith("_sign") || id.endsWith("_hanging_sign")) return 2.0D;
        return null;
    }

    private static Double sandGlassSellPrice(String id) {
        return switch (id) {
            case "glass", "tinted_glass", "sandstone", "red_sandstone", "smooth_sandstone", "smooth_red_sandstone" -> 2.0D;
            case "glass_pane" -> 1.0D;
            case "sandstone_slab", "red_sandstone_slab", "smooth_sandstone_slab", "smooth_red_sandstone_slab" -> 1.0D;
            case "sandstone_stairs", "red_sandstone_stairs", "smooth_sandstone_stairs", "smooth_red_sandstone_stairs" -> 3.0D;
            default -> {
                if (id.endsWith("_glass")) yield 2.0D;
                if (id.endsWith("_glass_pane")) yield 1.0D;
                yield null;
            }
        };
    }

    private static Double clayBrickSellPrice(String id) {
        if (id.endsWith("_terracotta")) return 2.0D;
        return switch (id) {
            case "bricks", "mud_bricks", "red_nether_bricks", "quartz_bricks" -> 8.0D;
            case "brick_slab", "mud_brick_slab", "nether_brick_slab" -> 4.0D;
            case "brick_stairs", "mud_brick_stairs", "nether_brick_stairs" -> 12.0D;
            case "mud_brick_wall" -> 8.0D;
            case "mud", "terracotta" -> 2.0D;
            case "packed_mud" -> 3.0D;
            case "nether_bricks" -> 4.0D;
            default -> null;
        };
    }

    private static Double copperSellPrice(String id) {
        if (!isCopper(id)) {
            return null;
        }
        if ("raw_copper".equals(id) || "copper_ingot".equals(id)) return 1.0D;
        if ("raw_copper_block".equals(id)) return 9.0D;
        if (id.endsWith("_slab")) return 4.5D;
        if (id.endsWith("_stairs")) return 13.5D;
        if (id.endsWith("_door") || id.endsWith("_trapdoor")) return 18.0D;
        return 9.0D;
    }

    private static Double buildingSellPrice(String id) {
        return switch (id) {
            case "obsidian" -> 20.0D;
            case "crying_obsidian" -> 30.0D;
            case "glowstone", "prismarine_bricks", "dark_prismarine" -> 8.0D;
            case "prismarine", "magma_block" -> 5.0D;
            case "sea_lantern" -> 10.0D;
            case "purpur_block", "packed_ice" -> 4.0D;
            case "prismarine_slab" -> 2.5D;
            case "prismarine_stairs" -> 7.5D;
            case "prismarine_brick_slab", "dark_prismarine_slab" -> 4.0D;
            case "prismarine_brick_stairs", "dark_prismarine_stairs" -> 12.0D;
            case "purpur_slab" -> 2.0D;
            case "purpur_stairs" -> 6.0D;
            case "slime_block", "honey_block" -> 9.0D;
            case "snow_block" -> 1.0D;
            default -> {
                if (id.endsWith("_wool")) yield 3.0D;
                if (id.endsWith("_carpet")) yield 1.0D;
                if (id.endsWith("_concrete") || id.endsWith("_concrete_powder")) yield 1.0D;
                yield null;
            }
        };
    }

    private static Double mobSellPrice(String id) {
        return switch (id) {
            case "arrow", "blaze_powder" -> 0.25D;
            case "blaze_rod", "bone", "ender_pearl", "feather", "rabbit_hide", "rotten_flesh", "slime_ball", "spider_eye", "string" -> 0.5D;
            case "fermented_spider_eye", "magma_cream" -> 0.75D;
            case "gunpowder" -> 1.0D;
            case "breeze_rod" -> 1.25D;
            case "ghast_tear" -> 2.5D;
            case "phantom_membrane" -> 8.0D;
            case "nautilus_shell", "rabbit_foot" -> 11.25D;
            default -> null;
        };
    }

    private static boolean isBambooChain(String id) {
        return "bamboo".equals(id) || id.startsWith("bamboo_");
    }

    private static boolean isFarmMachineItem(String id) {
        return switch (id) {
            case "wheat_seeds", "beetroot_seeds", "melon_seeds", "pumpkin_seeds", "torchflower_seeds", "pitcher_pod",
                    "cactus", "kelp", "sugar_cane", "melon_slice", "melon", "pumpkin", "wheat", "beetroot",
                    "carrot", "potato", "cocoa_beans", "nether_wart", "chorus_fruit", "chorus_flower",
                    "glow_berries", "sweet_berries", "brown_mushroom", "red_mushroom", "crimson_fungus",
                    "warped_fungus", "mangrove_propagule", "azalea", "flowering_azalea" -> true;
            default -> id.endsWith("_sapling");
        };
    }

    private static boolean isFixedStone(String id) {
        return "stone".equals(id)
                || "cobblestone".equals(id)
                || "deepslate".equals(id)
                || "cobbled_deepslate".equals(id)
                || "andesite".equals(id)
                || "granite".equals(id)
                || "diorite".equals(id)
                || "tuff".equals(id)
                || "blackstone".equals(id)
                || "netherrack".equals(id);
    }

    private static boolean isHardStone(String id) {
        return "deepslate".equals(id)
                || "cobbled_deepslate".equals(id)
                || id.contains("blackstone")
                || id.contains("basalt")
                || "end_stone".equals(id);
    }

    private static boolean isLog(String id) {
        return id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae");
    }

    private static boolean isWood(String id) {
        return isLog(id)
                || id.endsWith("_planks")
                || isBambooChain(id)
                || isWoodFamily(id);
    }

    private static boolean isWoodFamily(String id) {
        return startsAny(id, "cherry_", "oak_", "spruce_", "birch_", "jungle_", "acacia_", "dark_oak_", "mangrove_", "crimson_", "warped_");
    }

    private static boolean isSandGlass(String id) {
        return "sand".equals(id)
                || "red_sand".equals(id)
                || "sandstone".equals(id)
                || "red_sandstone".equals(id)
                || "smooth_sandstone".equals(id)
                || "smooth_red_sandstone".equals(id)
                || isGlass(id);
    }

    private static boolean isGlass(String id) {
        return "glass".equals(id)
                || "glass_pane".equals(id)
                || "tinted_glass".equals(id)
                || id.endsWith("_glass")
                || id.endsWith("_glass_pane");
    }

    private static boolean isClayBrick(String id) {
        return "clay".equals(id)
                || "bricks".equals(id)
                || id.startsWith("brick_")
                || "mud".equals(id)
                || "packed_mud".equals(id)
                || "mud_bricks".equals(id)
                || id.startsWith("mud_brick_")
                || "terracotta".equals(id)
                || id.endsWith("_terracotta")
                || "nether_bricks".equals(id)
                || "red_nether_bricks".equals(id)
                || "quartz_bricks".equals(id)
                || "tuff_bricks".equals(id)
                || "prismarine_bricks".equals(id);
    }

    private static boolean isWool(String id) {
        return id.endsWith("_wool") || id.endsWith("_carpet");
    }

    private static boolean isCopper(String id) {
        return "copper_block".equals(id)
                || "cut_copper".equals(id)
                || "chiseled_copper".equals(id)
                || startsAny(id, "exposed_", "weathered_", "oxidized_", "waxed_")
                || id.contains("copper");
    }

    private static boolean isOreGroup(String id) {
        return id.contains("ore")
                || id.startsWith("raw_")
                || id.endsWith("_ingot")
                || id.endsWith("_nugget")
                || "diamond".equals(id)
                || "emerald".equals(id)
                || "coal".equals(id)
                || "charcoal".equals(id)
                || "lapis_lazuli".equals(id)
                || "redstone".equals(id)
                || "quartz".equals(id)
                || "amethyst_shard".equals(id)
                || "netherite_scrap".equals(id)
                || "netherite_ingot".equals(id);
    }

    private static boolean isStone(String id) {
        return "stone".equals(id)
                || "cobblestone".equals(id)
                || "deepslate".equals(id)
                || "cobbled_deepslate".equals(id)
                || "blackstone".equals(id)
                || "tuff".equals(id)
                || "calcite".equals(id)
                || "dripstone_block".equals(id)
                || "basalt".equals(id)
                || "end_stone".equals(id)
                || "netherrack".equals(id)
                || "diorite".equals(id)
                || "andesite".equals(id)
                || "granite".equals(id)
                || id.startsWith("polished_")
                || id.startsWith("stone_")
                || id.contains("_stone")
                || id.contains("cobblestone")
                || id.contains("deepslate")
                || id.contains("blackstone")
                || id.contains("tuff");
    }

    private static boolean isBuilding(String id) {
        return id.endsWith("_slab")
                || id.endsWith("_stairs")
                || id.endsWith("_wall")
                || "prismarine".equals(id)
                || "purpur".equals(id)
                || "obsidian".equals(id)
                || "crying_obsidian".equals(id)
                || "glowstone".equals(id)
                || "snow_block".equals(id)
                || "packed_ice".equals(id)
                || "honey_block".equals(id)
                || "slime_block".equals(id)
                || "magma_block".equals(id);
    }

    private static boolean startsAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String materialId(ShopEntry entry) {
        if (entry.material != null && !entry.material.isBlank()) {
            return entry.material;
        }
        return entry.id == null ? "" : entry.id;
    }

    private static String path(String materialId) {
        String normalized = materialId == null ? "" : materialId.trim().toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        return colon >= 0 ? normalized.substring(colon + 1) : normalized;
    }

    private record Snapshot(String priceGroup, double buyPrice, double sellPrice, boolean dynamicPricing,
                            double minMultiplier, double maxMultiplier, String category) {
        private static Snapshot of(ShopEntry entry) {
            return new Snapshot(entry.priceGroup, entry.buyPrice, entry.sellPrice, entry.dynamicPricing,
                    entry.minMultiplier, entry.maxMultiplier, entry.category);
        }
    }
}
