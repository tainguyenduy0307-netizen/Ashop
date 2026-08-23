package com.tai.adminshop.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.notification.DiscordWebhookNotifier;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PriceWindowManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter WINDOW_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter BROKEN_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path configDir = FabricLoader.getInstance().getConfigDir().resolve("adminshop");
    private final Path configFile = configDir.resolve("price_windows.json");
    private final Random random = new Random();
    private PriceWindowConfig config = new PriceWindowConfig();

    public synchronized void load() {
        try {
            Files.createDirectories(configDir);
            if (Files.exists(configFile)) {
                try (Reader reader = Files.newBufferedReader(configFile)) {
                    PriceWindowConfig loaded = GSON.fromJson(reader, PriceWindowConfig.class);
                    config = loaded == null ? new PriceWindowConfig() : loaded;
                }
            } else {
                config = new PriceWindowConfig();
            }

            if (config.groupMultipliers == null) {
                config.groupMultipliers = new LinkedHashMap<>();
            }

            String currentWindowKey = currentWindowKey();
            if (!currentWindowKey.equals(config.windowKey)) {
                reroll();
                return;
            }

            if (ensureMissingMultipliers()) {
                save();
            }
        } catch (Exception e) {
            AdminShopMod.LOGGER.warn("Failed to load AdminShop price windows; regenerating current window", e);
            backupBrokenPriceWindowFile();
            config = new PriceWindowConfig();
            reroll();
        }
    }

    public synchronized void reload() {
        load();
    }

    public synchronized void reroll() {
        Map<String, Double> oldMultipliers = new LinkedHashMap<>(config.groupMultipliers);
        config.windowKey = currentWindowKey();
        config.generatedAt = System.currentTimeMillis();
        config.groupMultipliers = new LinkedHashMap<>();
        for (ShopEntry entry : AdminShopMod.SHOP_MANAGER.all()) {
            if (isDynamic(entry)) {
                config.groupMultipliers.computeIfAbsent(priceGroup(entry), ignored -> randomMultiplier(entry));
            }
        }
        save();
        notifyPriceChanges(oldMultipliers, config.groupMultipliers);
    }

    public synchronized void checkWindow() {
        if (!currentWindowKey().equals(config.windowKey)) {
            reroll();
        }
    }

    public synchronized double multiplier(ShopEntry entry) {
        if (!isDynamic(entry)) {
            return 1.0D;
        }

        loadIfOutdated();
        String group = priceGroup(entry);
        Double multiplier = config.groupMultipliers.get(group);
        if (multiplier == null) {
            multiplier = randomMultiplier(entry);
            config.groupMultipliers.put(group, multiplier);
            save();
        }
        return multiplier;
    }

    public synchronized double effectiveBuyPrice(ShopEntry entry) {
        return effectivePrice(entry.buyPrice, multiplier(entry));
    }

    public synchronized double effectiveSellPrice(ShopEntry entry) {
        if (entry.sellPrice <= 0) {
            return 0.0D;
        }

        double buyPrice = effectiveBuyPrice(entry);
        double sellPrice = effectivePrice(entry.sellPrice, multiplier(entry));
        if (sellPrice >= buyPrice) {
            return Math.max(0.0D, buyPrice * 0.6D);
        }
        return sellPrice;
    }

    public synchronized String windowKey() {
        loadIfOutdated();
        return config.windowKey;
    }

    public synchronized String windowName() {
        return LocalTime.now().getHour() < 12 ? "AM" : "PM";
    }

    public synchronized String nextUpdateLabel() {
        return nextUpdateTime() + " (" + timeUntilNextUpdate() + ")";
    }

    public synchronized String nextUpdateTime() {
        return LocalTime.now().getHour() < 12 ? "12:00" : "00:00";
    }

    private String timeUntilNextUpdate() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.getHour() < 12
                ? LocalDateTime.of(now.toLocalDate(), LocalTime.NOON)
                : LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.MIDNIGHT);
        long millisRemaining = Math.max(0L, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis());
        long minutesRemaining = Math.max(1L, millisRemaining / 60_000L);
        long hours = minutesRemaining / 60L;
        long minutes = minutesRemaining % 60L;
        return hours + "h " + minutes + "m";
    }

    public synchronized PriceWindowInfo info(ShopEntry entry) {
        double multiplier = multiplier(entry);
        return new PriceWindowInfo(
                entry.dynamicPricing,
                priceGroup(entry),
                multiplier,
                entry.buyPrice,
                effectivePrice(entry.buyPrice, multiplier),
                entry.sellPrice,
                effectiveSellPrice(entry),
                windowKey(),
                windowName(),
                nextUpdateLabel()
        );
    }

    private void loadIfOutdated() {
        if (!currentWindowKey().equals(config.windowKey)) {
            load();
        }
    }

    private boolean ensureMissingMultipliers() {
        boolean changed = false;
        for (ShopEntry entry : AdminShopMod.SHOP_MANAGER.all()) {
            if (isDynamic(entry) && !config.groupMultipliers.containsKey(priceGroup(entry))) {
                config.groupMultipliers.put(priceGroup(entry), randomMultiplier(entry));
                changed = true;
            }
        }
        return changed;
    }

    private void save() {
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to save AdminShop price windows", e);
        }
    }

    private static boolean isDynamic(ShopEntry entry) {
        return entry != null
                && entry.dynamicPricing
                && !"tickets".equals(ShopEntry.normalizeCategory(entry.category))
                && !"crates".equals(ShopEntry.normalizeCategory(entry.category))
                && Boolean.TRUE.equals(AdminShopMod.SETTINGS_MANAGER.settings().dynamicPricingEnabled);
    }

    private double randomMultiplier(ShopEntry entry) {
        double min = sanitizeMultiplier(entry.minMultiplier, 1.0D);
        double max = sanitizeMultiplier(entry.maxMultiplier, min);
        if (max < min) {
            double swap = min;
            min = max;
            max = swap;
        }
        if (Double.compare(min, max) == 0) {
            return min;
        }
        return min + (max - min) * random.nextDouble();
    }

    private static double sanitizeMultiplier(double multiplier, double fallback) {
        if (Double.isFinite(multiplier) && multiplier >= 0.0D) {
            return multiplier;
        }
        return fallback;
    }

    private static double effectivePrice(double basePrice, double multiplier) {
        if (basePrice <= 0) {
            return 0.0D;
        }

        double value = basePrice * multiplier;
        if (!Double.isFinite(value)) {
            return Double.MAX_VALUE;
        }
        return Math.max(0.01D, value);
    }

    public synchronized List<DiscordWebhookNotifier.PriceChange> previewRerollChanges() {
        loadIfOutdated();
        if (ensureMissingMultipliers()) {
            save();
        }

        Map<String, Double> simulatedMultipliers = new LinkedHashMap<>();
        for (ShopEntry entry : AdminShopMod.SHOP_MANAGER.all()) {
            if (isDynamic(entry)) {
                simulatedMultipliers.computeIfAbsent(priceGroup(entry), ignored -> randomMultiplier(entry));
            }
        }
        return buildPriceChanges(config.groupMultipliers, simulatedMultipliers);
    }

    public synchronized int dynamicItemsCount() {
        int count = 0;
        for (ShopEntry entry : AdminShopMod.SHOP_MANAGER.all()) {
            if (isDynamic(entry)) {
                count++;
            }
        }
        return count;
    }

    public synchronized int dynamicGroupsCount() {
        return config.groupMultipliers.size();
    }

    private void notifyPriceChanges(Map<String, Double> oldMultipliers, Map<String, Double> newMultipliers) {
        if (oldMultipliers.isEmpty()) {
            return;
        }

        AdminShopMod.DISCORD_WEBHOOK_NOTIFIER.notifyPriceWindowUpdate(nextUpdateTime(), buildPriceChanges(oldMultipliers, newMultipliers));
    }

    private List<DiscordWebhookNotifier.PriceChange> buildPriceChanges(Map<String, Double> oldMultipliers, Map<String, Double> newMultipliers) {
        List<DiscordWebhookNotifier.PriceChange> changes = new ArrayList<>();
        for (String group : newMultipliers.keySet()) {
            Double newMultiplier = newMultipliers.get(group);
            if (newMultiplier == null) {
                continue;
            }

            double percentChange = (newMultiplier - 1.0D) * 100.0D;
            if (Double.compare(percentChange, 0.0D) == 0) {
                continue;
            }

            changes.add(new DiscordWebhookNotifier.PriceChange(
                    group,
                    marketDisplayName(group),
                    0.0D,
                    0.0D,
                    newMultiplier,
                    percentChange
            ));
        }
        return changes;
    }

    private void backupBrokenPriceWindowFile() {
        try {
            if (Files.exists(configFile)) {
                Path brokenFile = configFile.resolveSibling("price_windows-" + LocalDateTime.now().format(BROKEN_FORMAT) + ".json.broken");
                Files.move(configFile, brokenFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException backupError) {
            AdminShopMod.LOGGER.warn("Failed to backup broken AdminShop price window file", backupError);
        }
    }

    private static String marketDisplayName(String group) {
        return formatDisplayName(group) + " Market";
    }

    private static String formatDisplayName(String group) {
        String normalized = group == null ? "" : group;
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
        return builder.isEmpty() ? normalized : builder.toString();
    }

    public static String priceGroup(ShopEntry entry) {
        if (entry == null) {
            return "misc";
        }
        if (entry.priceGroup != null && !entry.priceGroup.isBlank()) {
            return normalizeGroup(entry.priceGroup);
        }
        return normalizeGroup(entry.category);
    }

    private static String normalizeGroup(String group) {
        String normalized = ShopEntry.normalizeCategory(group);
        if ("tickets".equals(normalized) || "crates".equals(normalized)) {
            return "misc";
        }
        return normalized;
    }

    private static String currentWindowKey() {
        LocalDate now = LocalDate.now();
        String suffix = LocalTime.now().getHour() < 12 ? "AM" : "PM";
        return WINDOW_DATE_FORMAT.format(now) + "-" + suffix;
    }

    public record PriceWindowInfo(boolean dynamic, String priceGroup, double multiplier, double baseBuy, double currentBuy, double baseSell,
                                  double currentSell, String windowKey, String windowName, String nextUpdate) {
    }

    private static final class PriceWindowConfig {
        private String windowKey = "";
        private long generatedAt;
        private Map<String, Double> groupMultipliers = new LinkedHashMap<>();
    }
}
