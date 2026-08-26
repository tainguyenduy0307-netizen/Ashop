package com.tai.adminshop.config;

import com.google.gson.Gson;
import com.tai.adminshop.economy.PaymentPlan;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductionBoosterCatalogTest {
	private static final Set<String> IDS = Set.of("legendary_booster", "drop_booster", "wild_rarity_booster", "shiny_booster");

	@Test void packagedCategoryContainsExactlyFourTypedSapphireOnlyBoosters() {
		var stream = getClass().getClassLoader().getResourceAsStream("default_categories/boosters.json");
		assertNotNull(stream);
		CategoryShopConfig config = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), CategoryShopConfig.class);
		assertEquals("boosters", config.id);
		assertEquals(4, config.items.size());
		assertEquals(IDS, config.items.stream().map(value -> value.id).collect(Collectors.toSet()));
		for (ShopEntry entry : config.items) {
			assertEquals(entry.id, entry.boosterId);
			assertEquals("BOOSTER", entry.rewardType);
			assertEquals("sapphire", entry.currency);
			assertEquals("SAPPHIRE_ONLY", entry.paymentMode);
			assertEquals(10, entry.buyPrice);
			assertEquals(0, entry.sellPrice);
			assertTrue(entry.shopOnly);
			assertFalse(entry.isCommandReward(), "booster delivery must use the typed API, not an arbitrary command");
		}
	}

	@Test void sapphireOnlyPlanNeverConsumesRubyOrAcceptsRubyFallback() {
		assertEquals(new PaymentPlan(10, 0), PaymentPlan.sapphireOnlyForPrice(10, 10));
		assertNull(PaymentPlan.sapphireOnlyForPrice(10, 7), "999 Ruby is intentionally not an input to this policy");
		assertEquals(0, PaymentPlan.sapphireOnlyForPrice(10, 10).rubySpent());
	}

	@Test void activationFailurePathRefundsTheExactPaymentAndDoesNotDispatchACommand() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/tai/adminshop/service/ShopService.java"));
		assertTrue(source.contains("UnovaCoreBoosterBridge.activate(player, entry.boosterId, amount)"));
		assertTrue(source.contains("PaymentPolicy.refund(player, payment.receipt())"));
		assertFalse(source.substring(source.indexOf("if (boosterReward)"), source.indexOf("} else if (commandReward)")).contains("runRewardCommand"));
	}
}
