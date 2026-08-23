package com.tai.adminshop.config;

import com.tai.adminshop.AdminShopMod;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;

public final class ShopCategory {
    private static final List<ShopCategory> DEFAULT_CATEGORIES = List.of(
            new ShopCategory("blocks", "Blocks", Identifier.ofVanilla("grass_block"), Items.GRASS_BLOCK),
            new ShopCategory("ores", "Ores", Identifier.ofVanilla("diamond_ore"), Items.DIAMOND_ORE),
            new ShopCategory("foods", "Foods", Identifier.ofVanilla("apple"), Items.APPLE),
            new ShopCategory("farming", "Farming", Identifier.ofVanilla("wheat"), Items.WHEAT),
            new ShopCategory("redstone", "Redstone", Identifier.ofVanilla("redstone"), Items.REDSTONE),
            new ShopCategory("decoration", "Decoration", Identifier.ofVanilla("flower_pot"), Items.FLOWER_POT),
            new ShopCategory("workstations", "Workstations", Identifier.ofVanilla("crafting_table"), Items.CRAFTING_TABLE),
            new ShopCategory("enchanting", "Enchanting", Identifier.ofVanilla("enchanting_table"), Items.ENCHANTING_TABLE),
            new ShopCategory("dyes", "Dyes", Identifier.ofVanilla("red_dye"), Items.RED_DYE),
            new ShopCategory("music", "Music", Identifier.ofVanilla("music_disc_cat"), Items.MUSIC_DISC_CAT),
            new ShopCategory("potions", "Potions", Identifier.ofVanilla("potion"), Items.POTION),
            new ShopCategory("mobs", "Mobs", Identifier.ofVanilla("bone"), Items.BONE),
            new ShopCategory("tools", "Tools", Identifier.ofVanilla("iron_pickaxe"), Items.IRON_PICKAXE),
            new ShopCategory("combat", "Combat", Identifier.ofVanilla("iron_sword"), Items.IRON_SWORD),
            new ShopCategory("misc", "Misc", Identifier.ofVanilla("barrel"), Items.BARREL),
            new ShopCategory("tickets", "Tickets", Identifier.ofVanilla("tripwire_hook"), Items.TRIPWIRE_HOOK)
    );

    private final String id;
    private final String displayName;
    private final Identifier iconId;
    private final Item icon;

    private ShopCategory(String id, String displayName, Identifier iconId, Item icon) {
        this.id = id;
        this.displayName = displayName;
        this.iconId = iconId;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Identifier iconId() {
        return iconId;
    }

    public Item icon() {
        return icon;
    }

    public int countItems() {
        return countItems(id);
    }

    public static List<ShopCategory> getDefaultCategories() {
        return DEFAULT_CATEGORIES;
    }

    public static List<ShopCategory> getAllCategories() {
        List<ShopCategory> categories = new ArrayList<>(DEFAULT_CATEGORIES);
        Set<String> defaultIds = new LinkedHashSet<>();
        for (ShopCategory category : DEFAULT_CATEGORIES) {
            defaultIds.add(category.id);
        }

        AdminShopMod.SHOP_MANAGER.all().stream()
                .map(entry -> ShopEntry.normalizeCategory(entry.category))
                .filter(category -> !defaultIds.contains(category))
                .distinct()
                .sorted(Comparator.naturalOrder())
                .map(category -> categoryFromConfig(category, null))
                .forEach(categories::add);

        for (CategoryShopConfig categoryConfig : AdminShopMod.SHOP_MANAGER.categoryConfigs()) {
            String id = ShopEntry.normalizeCategory(categoryConfig.id);
            if (defaultIds.contains(id) || categories.stream().anyMatch(category -> category.id.equals(id))) {
                continue;
            }
            categories.add(categoryFromConfig(id, categoryConfig));
        }

        return categories;
    }

    public static Optional<ShopCategory> getById(String id) {
        String normalized = ShopEntry.normalizeCategory(id);
        return getAllCategories().stream()
                .filter(category -> category.id.equals(normalized))
                .findFirst();
    }

    public static Optional<ShopCategory> resolve(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = ShopEntry.normalizeCategory(value);
        String displayKey = compact(value);
        return getAllCategories().stream()
                .filter(category -> category.id.equals(normalized)
                        || compact(category.displayName).equals(displayKey))
                .findFirst();
    }

    public static int countItems(String category) {
        String normalized = ShopEntry.normalizeCategory(category);
        int count = 0;
        for (ShopEntry entry : AdminShopMod.SHOP_MANAGER.all()) {
            if (normalized.equals(ShopEntry.normalizeCategory(entry.category))) {
                count++;
            }
        }
        return count;
    }

    public static String displayName(String id) {
        String normalized = ShopEntry.normalizeCategory(id);
        return DEFAULT_CATEGORIES.stream()
                .filter(category -> category.id.equals(normalized))
                .findFirst()
                .map(ShopCategory::displayName)
                .orElseGet(() -> formatDisplayName(normalized));
    }

    private static ShopCategory categoryFromConfig(String id, CategoryShopConfig config) {
        String normalized = ShopEntry.normalizeCategory(id);
        String displayName = config != null && config.displayName != null && !config.displayName.isBlank()
                ? config.displayName
                : formatDisplayName(normalized);
        Identifier iconId = Identifier.tryParse(config == null ? "" : String.valueOf(config.icon));
        if (iconId == null) {
            iconId = Identifier.ofVanilla("barrel");
        }
        Item icon = Registries.ITEM.getOrEmpty(iconId).orElse(Items.BARREL);
        return new ShopCategory(normalized, displayName, iconId, icon);
    }

    private static String formatDisplayName(String id) {
        String normalized = ShopEntry.normalizeCategory(id);
        String[] parts = normalized.split("[_\\-\\s]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.isEmpty() ? "Misc" : builder.toString();
    }

    private static String compact(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
    }
}
