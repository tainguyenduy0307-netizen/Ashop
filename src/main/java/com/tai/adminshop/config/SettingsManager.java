package com.tai.adminshop.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tai.adminshop.AdminShopMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SettingsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter BROKEN_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path configDir = FabricLoader.getInstance().getConfigDir().resolve("adminshop");
    private final Path configFile = configDir.resolve("settings.json");
    private Settings settings = new Settings();

    public synchronized void load() {
        try {
            Files.createDirectories(configDir);
            if (Files.exists(configFile)) {
                try (Reader reader = Files.newBufferedReader(configFile)) {
                    Settings loaded = GSON.fromJson(reader, Settings.class);
                    settings = loaded == null ? new Settings() : loaded;
                }
            } else {
                settings = new Settings();
                save();
            }
            sanitize();
            save();
        } catch (Exception e) {
            AdminShopMod.LOGGER.warn("Failed to load AdminShop settings; using defaults", e);
            backupBrokenSettings();
            settings = new Settings();
            save();
        }
    }

    public synchronized void reload() {
        load();
    }

    public synchronized Settings settings() {
        return settings;
    }

    private void save() {
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(settings, writer);
            }
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to save AdminShop settings", e);
        }
    }

    private void sanitize() {
        if (settings.dynamicPricingEnabled == null) {
            settings.dynamicPricingEnabled = true;
        }
        if (settings.discordWebhookEnabled == null) {
            settings.discordWebhookEnabled = false;
        }
        if (settings.discordWebhookUrl == null) {
            settings.discordWebhookUrl = "";
        }
        if (!Double.isFinite(settings.discordWebhookMinChangePercent) || settings.discordWebhookMinChangePercent < 0.0D) {
            settings.discordWebhookMinChangePercent = 5.0D;
        }
        if (settings.discordWebhookMaxItemsPerSection <= 0) {
            settings.discordWebhookMaxItemsPerSection = 10;
        }
        if (settings.sendWebhookWhenNoSignificantChanges == null) {
            settings.sendWebhookWhenNoSignificantChanges = false;
        }
        if (settings.discordListingWebhookEnabled == null) {
            settings.discordListingWebhookEnabled = true;
        }
        if (settings.discordListingWebhookUrl == null) {
            settings.discordListingWebhookUrl = "";
        }
        if (settings.discordListingWebhookMentionRoleId == null) {
            settings.discordListingWebhookMentionRoleId = "";
        }
        if (!Double.isFinite(settings.discordListingWebhookMinPrice) || settings.discordListingWebhookMinPrice < 0.0D) {
            settings.discordListingWebhookMinPrice = 0.0D;
        }
        if (settings.discordListingWebhookNotifyItems == null) {
            settings.discordListingWebhookNotifyItems = true;
        }
        if (settings.discordListingWebhookNotifyPokemon == null) {
            settings.discordListingWebhookNotifyPokemon = true;
        }
    }

    private void backupBrokenSettings() {
        try {
            if (Files.exists(configFile)) {
                Path brokenFile = configFile.resolveSibling("settings-" + LocalDateTime.now().format(BROKEN_FORMAT) + ".json.broken");
                Files.move(configFile, brokenFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException backupError) {
            AdminShopMod.LOGGER.warn("Failed to backup broken AdminShop settings file", backupError);
        }
    }

    public static class Settings {
        public Boolean dynamicPricingEnabled = true;
        public Boolean discordWebhookEnabled = false;
        public String discordWebhookUrl = "";
        public double discordWebhookMinChangePercent = 5.0D;
        public int discordWebhookMaxItemsPerSection = 10;
        public Boolean sendWebhookWhenNoSignificantChanges = false;
        public Boolean discordListingWebhookEnabled = true;
        public String discordListingWebhookUrl = "";
        public String discordListingWebhookMentionRoleId = "";
        public double discordListingWebhookMinPrice = 0.0D;
        public Boolean discordListingWebhookNotifyItems = true;
        public Boolean discordListingWebhookNotifyPokemon = true;
    }
}
