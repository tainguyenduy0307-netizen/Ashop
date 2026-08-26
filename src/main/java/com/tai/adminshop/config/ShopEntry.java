package com.tai.adminshop.config;

import java.util.List;

public class ShopEntry {
    public String id;
    public String material;
    public String name;
    public List<String> lore;
    public int quantity = 1;
    public int slot = -1;
    public int page = 1;
    public String category = "misc";
    public String priceGroup;
    public String currency = "dollars";
    public double buyPrice;
    public double sellPrice;
    public boolean dynamicPricing;
    public double minMultiplier = 1.0D;
    public double maxMultiplier = 1.0D;
    public String itemData;
    public boolean purchaseLimitEnabled;
    public int purchaseLimit;
    public String purchaseLimitPeriod = "NONE";
    public String purchaseLimitLabel;
    public String rewardType = "ITEM";
    public String rewardCommand;
	public String boosterId;
	public String paymentMode = "DEFAULT";
    /** Curated policy marker: special item is buy-only and must not use dynamic pricing. */
    public boolean shopOnly;

    public ShopEntry() {
    }

    public ShopEntry(String id, double buyPrice, double sellPrice, String itemData) {
        this(id, "misc", buyPrice, sellPrice, itemData);
    }

    public ShopEntry(String id, String category, double buyPrice, double sellPrice, String itemData) {
        this.id = id;
        this.category = normalizeCategory(category);
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.itemData = itemData;
    }

    public static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "misc";
        }
        String normalized = category.trim().toLowerCase();
        if ("keys".equals(normalized)) {
            return "tickets";
        }
        if ("mob_drops".equals(normalized)) {
            return "mobs";
        }
        return normalized;
    }

    public boolean isCommandReward() {
        return "COMMAND".equalsIgnoreCase(rewardType);
    }
	public boolean isBoosterReward() { return "BOOSTER".equalsIgnoreCase(rewardType); }
}
