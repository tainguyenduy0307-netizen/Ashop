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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class PurchaseLimitManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter BROKEN_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    private final Path configDir = FabricLoader.getInstance().getConfigDir().resolve("adminshop");
    private final Path limitsFile = configDir.resolve("purchase_limits.json");
    private PurchaseLimitData data = new PurchaseLimitData();

    public synchronized void load() {
        try {
            Files.createDirectories(configDir);
            if (!Files.exists(limitsFile)) {
                data = new PurchaseLimitData();
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(limitsFile)) {
                PurchaseLimitData loaded = GSON.fromJson(reader, PurchaseLimitData.class);
                data = loaded == null ? new PurchaseLimitData() : loaded;
            } catch (Exception e) {
                backupBrokenFile();
                data = new PurchaseLimitData();
                save();
                AdminShopMod.LOGGER.warn("AdminShop purchase_limits.json was corrupt and has been reset", e);
                return;
            }

            sanitize();
            save();
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to load AdminShop purchase limits", e);
            data = new PurchaseLimitData();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(configDir);
            sanitize();
            try (Writer writer = Files.newBufferedWriter(limitsFile)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to save AdminShop purchase limits", e);
        }
    }

    public synchronized PurchaseLimitStatus status(UUID playerUuid, ShopEntry entry) {
        PurchaseLimitPeriod period = period(entry);
        int limit = limit(entry);
        if (!isEnabled(entry, period, limit)) {
            return PurchaseLimitStatus.unlimited();
        }

        PurchaseLimitPeriod.Window window = period.currentWindow();
        ItemLimit itemLimit = itemLimit(playerUuid, entry.id);
        if (!window.key().equals(itemLimit.windowKey)) {
            itemLimit.windowKey = window.key();
            itemLimit.count = 0;
        }

        int count = Math.max(0, itemLimit.count);
        return new PurchaseLimitStatus(true, limit, count, window.key(), window.timeUntilReset(), count < limit);
    }

    public synchronized boolean increment(UUID playerUuid, ShopEntry entry, int amount) {
        if (amount <= 0) {
            return false;
        }
        PurchaseLimitStatus status = status(playerUuid, entry);
        if (!status.limited()) {
            return true;
        }
        if (status.count() + amount > status.limit()) {
            return false;
        }

        ItemLimit itemLimit = itemLimit(playerUuid, entry.id);
        itemLimit.windowKey = status.windowKey();
        itemLimit.count = status.count() + amount;
        save();
        return true;
    }

    public synchronized boolean reset(UUID playerUuid, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        Map<String, ItemLimit> playerLimits = data.players.get(playerUuid.toString());
        if (playerLimits == null) {
            return false;
        }
        boolean removed = playerLimits.remove(itemId) != null;
        if (playerLimits.isEmpty()) {
            data.players.remove(playerUuid.toString());
        }
        if (removed) {
            save();
        }
        return removed;
    }

    public static boolean isLimited(ShopEntry entry) {
        PurchaseLimitPeriod period = period(entry);
        return isEnabled(entry, period, limit(entry));
    }

    public static PurchaseLimitPeriod period(ShopEntry entry) {
        return PurchaseLimitPeriod.parse(entry == null ? null : entry.purchaseLimitPeriod);
    }

    public static int limit(ShopEntry entry) {
        return entry == null ? 0 : entry.purchaseLimit;
    }

    public static String label(ShopEntry entry) {
        PurchaseLimitPeriod period = period(entry);
        int limit = limit(entry);
        if (entry != null && entry.purchaseLimitLabel != null && !entry.purchaseLimitLabel.isBlank()) {
            return entry.purchaseLimitLabel;
        }
        return period.defaultLabel(limit);
    }

    public static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1, minutes) + "m";
    }

    private static boolean isEnabled(ShopEntry entry, PurchaseLimitPeriod period, int limit) {
        return entry != null
                && entry.purchaseLimitEnabled
                && entry.id != null
                && !entry.id.isBlank()
                && limit > 0
                && period != PurchaseLimitPeriod.NONE;
    }

    private ItemLimit itemLimit(UUID playerUuid, String itemId) {
        Map<String, ItemLimit> playerLimits = data.players.computeIfAbsent(playerUuid.toString(), ignored -> new LinkedHashMap<>());
        return playerLimits.computeIfAbsent(itemId, ignored -> new ItemLimit());
    }

    private void sanitize() {
        if (data == null) {
            data = new PurchaseLimitData();
        }
        if (data.players == null) {
            data.players = new LinkedHashMap<>();
        }

        Iterator<Map.Entry<String, Map<String, ItemLimit>>> playerIterator = data.players.entrySet().iterator();
        while (playerIterator.hasNext()) {
            Map.Entry<String, Map<String, ItemLimit>> playerEntry = playerIterator.next();
            if (playerEntry.getKey() == null || playerEntry.getKey().isBlank() || playerEntry.getValue() == null) {
                playerIterator.remove();
                continue;
            }

            Iterator<Map.Entry<String, ItemLimit>> itemIterator = playerEntry.getValue().entrySet().iterator();
            while (itemIterator.hasNext()) {
                Map.Entry<String, ItemLimit> itemEntry = itemIterator.next();
                ItemLimit limit = itemEntry.getValue();
                if (itemEntry.getKey() == null || itemEntry.getKey().isBlank()
                        || limit == null || limit.windowKey == null || limit.windowKey.isBlank()) {
                    itemIterator.remove();
                    continue;
                }
                if (limit.count < 0) {
                    limit.count = 0;
                }
            }

            if (playerEntry.getValue().isEmpty()) {
                playerIterator.remove();
            }
        }
    }

    private void backupBrokenFile() {
        try {
            if (!Files.exists(limitsFile)) {
                return;
            }
            Path broken = limitsFile.resolveSibling("purchase_limits-" + LocalDateTime.now().format(BROKEN_FORMAT) + ".json.broken");
            Files.copy(limitsFile, broken, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            AdminShopMod.LOGGER.warn("Failed to backup corrupt purchase_limits.json", e);
        }
    }

    private static final class PurchaseLimitData {
        private Map<String, Map<String, ItemLimit>> players = new LinkedHashMap<>();
    }

    private static final class ItemLimit {
        private String windowKey = "";
        private int count;
    }

    public record PurchaseLimitStatus(boolean limited, int limit, int count, String windowKey, Duration resetsIn, boolean canBuy) {
        public static PurchaseLimitStatus unlimited() {
            return new PurchaseLimitStatus(false, 0, 0, "", Duration.ZERO, true);
        }
    }
}
