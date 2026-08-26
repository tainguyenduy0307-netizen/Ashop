package com.tai.adminshop.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Validates the packaged, curated catalog against the real Cobblemon 1.7.3 API on testRuntimeClasspath. */
class ProductionCobblemonCatalogTest {
    private static final List<String> CATEGORIES = List.of(
            "pokeballs", "medicine", "battle_items", "held_items", "evolution_items", "berries", "utility");

    @Test void productionCatalogUsesRealCobblemonRegistryFieldsAndExcludesRelics() throws Exception {
        Collection<ShopEntry> entries = entries();
        assertEquals(41, entries.size());
        assertEquals(7, CATEGORIES.size());
        Set<String> ids = new HashSet<>();
        Set<String> materials = new HashSet<>();
        for (ShopEntry entry : entries) {
            assertTrue(ids.add(entry.id), () -> "duplicate entry id " + entry.id);
            assertTrue(materials.add(entry.material), () -> "duplicate canonical item " + entry.material);
            assertTrue(entry.material.startsWith("cobblemon:"));
            assertFalse(entry.material.contains("debug") || entry.material.contains("test"));
            assertFalse(entry.material.equals("unova_pokemon_rpg:dialga_relic"));
            assertFalse(entry.material.equals("unova_pokemon_rpg:palkia_relic"));
            assertFalse(entry.material.equals("unova_pokemon_rpg:giratina_relic"));
            assertCobblemonItemFieldExists(entry.material);
        }
        assertRepresentative(entries, "pokeballs", "cobblemon:poke_ball");
        assertRepresentative(entries, "medicine", "cobblemon:potion");
        assertRepresentative(entries, "battle_items", "cobblemon:x_attack");
        assertRepresentative(entries, "held_items", "cobblemon:leftovers");
        assertRepresentative(entries, "evolution_items", "cobblemon:fire_stone");
        assertRepresentative(entries, "berries", "cobblemon:oran_berry");
        assertRepresentative(entries, "utility", "cobblemon:exp_share");
    }

    @Test void shopOnlyCatalogIsBuyOnlyFixedPriceAndMasterBallIsProtected() throws Exception {
        Collection<ShopEntry> entries = entries();
        List<ShopEntry> shopOnly = entries.stream().filter(entry -> entry.shopOnly).toList();
        assertFalse(shopOnly.isEmpty());
        for (ShopEntry entry : shopOnly) {
            assertCobblemonItemFieldExists(entry.material);
            assertEquals(0.0D, entry.sellPrice, entry.id);
            assertFalse(entry.dynamicPricing, entry.id);
            assertTrue(entry.buyPrice > 0, entry.id);
        }
        ShopEntry masterBall = entries.stream().filter(entry -> "cobblemon:master_ball".equals(entry.material)).findFirst().orElseThrow();
        assertTrue(masterBall.shopOnly);
        assertEquals(0.0D, masterBall.sellPrice);
        assertFalse(masterBall.dynamicPricing);
        assertNotEquals("ruby", masterBall.currency);
    }

    private static void assertRepresentative(Collection<ShopEntry> entries, String category, String material) {
        assertTrue(entries.stream().anyMatch(entry -> category.equals(entry.category) && material.equals(entry.material)),
                () -> "missing representative " + material + " in " + category);
    }

    private static Collection<ShopEntry> entries() {
        Gson gson = new Gson();
        List<ShopEntry> result = new ArrayList<>();
        for (String category : CATEGORIES) {
            var stream = ProductionCobblemonCatalogTest.class.getClassLoader()
                    .getResourceAsStream("default_categories/" + category + ".json");
            assertNotNull(stream, () -> "missing packaged category " + category);
            CategoryShopConfig config = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), CategoryShopConfig.class);
            assertNotNull(config);
            assertEquals(category, config.id);
            assertNotNull(config.items);
            assertFalse(config.items.isEmpty());
            result.addAll(config.items);
        }
        return result;
    }

    private static void assertCobblemonItemFieldExists(String itemId) throws IOException {
        assertTrue(itemId.startsWith("cobblemon:"));
        String fieldName = itemId.substring("cobblemon:".length()).toUpperCase(Locale.ROOT);
        // Loom's plain JUnit worker intentionally does not bootstrap Minecraft's mapped runtime.
        // Inspect the actual Cobblemon class resource on testRuntimeClasspath instead of creating a
        // fake registry or triggering its Minecraft-linked static initializer.
        var classResource = ProductionCobblemonCatalogTest.class.getClassLoader()
                .getResourceAsStream("com/cobblemon/mod/common/CobblemonItems.class");
        assertNotNull(classResource, "Cobblemon 1.7.3 test runtime is missing CobblemonItems");
        String classBytes = new String(classResource.readAllBytes(), StandardCharsets.ISO_8859_1);
        assertTrue(classBytes.contains(fieldName), () -> "CobblemonItems lacks canonical field " + fieldName);
    }
}
