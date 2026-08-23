package com.tai.adminshop;

import com.tai.adminshop.command.AdminShopCommand;
import com.tai.adminshop.config.PriceWindowManager;
import com.tai.adminshop.config.PurchaseLimitManager;
import com.tai.adminshop.config.SettingsManager;
import com.tai.adminshop.config.ShopManager;
import com.tai.adminshop.config.StoreManager;
import com.tai.adminshop.economy.GemsManager;
import com.tai.adminshop.integration.AdminShopPlaceholders;
import com.tai.adminshop.notification.DiscordWebhookNotifier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminShopMod implements ModInitializer {
    public static final String MOD_ID = "adminshop";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ShopManager SHOP_MANAGER = new ShopManager();
    public static final StoreManager STORE_MANAGER = new StoreManager();
    public static final GemsManager GEMS_MANAGER = new GemsManager();
    public static final PriceWindowManager PRICE_WINDOW_MANAGER = new PriceWindowManager();
    public static final PurchaseLimitManager PURCHASE_LIMIT_MANAGER = new PurchaseLimitManager();
    public static final SettingsManager SETTINGS_MANAGER = new SettingsManager();
    public static final DiscordWebhookNotifier DISCORD_WEBHOOK_NOTIFIER = new DiscordWebhookNotifier();
    private static final int PRICE_WINDOW_CHECK_INTERVAL_TICKS = 20 * 60 * 5;
    private int priceWindowCheckTicks;

    @Override
    public void onInitialize() {
        SHOP_MANAGER.load();
        STORE_MANAGER.load();
        GEMS_MANAGER.load();
        SETTINGS_MANAGER.load();
        PRICE_WINDOW_MANAGER.load();
        PURCHASE_LIMIT_MANAGER.load();
        AdminShopPlaceholders.register();
        AdminShopCommand.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            priceWindowCheckTicks++;
            if (priceWindowCheckTicks >= PRICE_WINDOW_CHECK_INTERVAL_TICKS) {
                priceWindowCheckTicks = 0;
                PRICE_WINDOW_MANAGER.checkWindow();
            }
        });
        LOGGER.info("AdminShop loaded");
    }
}

