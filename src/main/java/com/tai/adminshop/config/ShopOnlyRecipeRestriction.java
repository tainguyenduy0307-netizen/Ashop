package com.tai.adminshop.config;

import com.tai.adminshop.AdminShopMod;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Removes only recipes whose output is explicitly marked {@code shopOnly} in curated config. */
public final class ShopOnlyRecipeRestriction {
    private ShopOnlyRecipeRestriction() {
    }

    public static int apply(MinecraftServer server) {
        if (server == null) return 0;
        Set<Identifier> protectedItems = new LinkedHashSet<>();
        for (ShopEntry entry : AdminShopMod.SHOP_MANAGER.all()) {
            if (!entry.shopOnly || entry.material == null) continue;
            Identifier id = Identifier.tryParse(entry.material);
            if (id != null) protectedItems.add(id);
        }
        if (protectedItems.isEmpty()) return 0;

        RecipeManager recipes = server.getRecipeManager();
        List<RecipeEntry<?>> retained = recipes.values().stream()
                .filter(entry -> !protectedItems.contains(net.minecraft.registry.Registries.ITEM.getId(
                        entry.value().getResult(server.getRegistryManager()).getItem())))
                .toList();
        int removed = recipes.values().size() - retained.size();
        if (removed > 0) {
            recipes.setRecipes(retained);
            AdminShopMod.LOGGER.info("Disabled {} recipe(s) for {} shop-only AdminShop item(s)", removed, protectedItems.size());
        }
        return removed;
    }
}
