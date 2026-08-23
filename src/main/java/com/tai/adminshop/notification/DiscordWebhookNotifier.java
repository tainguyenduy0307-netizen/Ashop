package com.tai.adminshop.notification;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.config.SettingsManager;
import com.tai.adminshop.util.PriceFormatter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class DiscordWebhookNotifier {
    private static final Gson GSON = new Gson();
    private static final int COLOR_BLUE = 0x3498DB;
    private static final int COLOR_GOLD = 0xF1C40F;
    private static final int COLOR_PURPLE = 0x9B59B6;
    private static final int COLOR_PINK = 0xFF69B4;
    private static final String MARKET_FOOTER = "Unova Cobblemon Market";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public void notifyPriceWindowUpdate(String nextUpdate, List<PriceChange> changes) {
        SettingsManager.Settings settings = AdminShopMod.SETTINGS_MANAGER.settings();
        if (!Boolean.TRUE.equals(settings.discordWebhookEnabled) || settings.discordWebhookUrl.isBlank()) {
            return;
        }

        PriceChangeSections sections = selectSections(changes);
        if (sections.increased().isEmpty() && sections.decreased().isEmpty()
                && !Boolean.TRUE.equals(settings.sendWebhookWhenNoSignificantChanges)) {
            return;
        }

        JsonObject embed = baseEmbed("AdminShop Price Update", "Gi\u00e1 shop \u0111\u00e3 c\u1eadp nh\u1eadt cho phi\u00ean 12 gi\u1edd m\u1edbi.");
        JsonArray fields = new JsonArray();
        fields.add(field("\uD83D\uDCC8 Th\u1ecb tr\u01b0\u1eddng t\u0103ng", formatSection(sections.increased()), false));
        fields.add(field("\uD83D\uDCC9 Th\u1ecb tr\u01b0\u1eddng gi\u1ea3m", formatSection(sections.decreased()), false));
        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Next update: " + nextUpdate);
        embed.add("footer", footer);

        JsonObject body = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        body.add("embeds", embeds);
        post(settings.discordWebhookUrl, body);
    }

    public boolean sendTestMessage() {
        SettingsManager.Settings settings = AdminShopMod.SETTINGS_MANAGER.settings();
        if (!Boolean.TRUE.equals(settings.discordWebhookEnabled) || settings.discordWebhookUrl.isBlank()) {
            return false;
        }

        JsonObject embed = baseEmbed("\uD83E\uDDEA AdminShop Webhook Test", "Webhook ho\u1ea1t \u0111\u1ed9ng b\u00ecnh th\u01b0\u1eddng.");
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Next update: " + AdminShopMod.PRICE_WINDOW_MANAGER.nextUpdateTime());
        embed.add("footer", footer);

        JsonObject body = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        body.add("embeds", embeds);
        post(settings.discordWebhookUrl, body);
        return true;
    }

    public boolean notifyListingCreated(ListingWebhookPayload listing) {
        SettingsManager.Settings settings = AdminShopMod.SETTINGS_MANAGER.settings();
        if (!shouldSendListingWebhook(settings, listing)) {
            return false;
        }

        JsonObject embed = listingEmbed(listing);
        JsonObject body = new JsonObject();
        String mentionRoleId = settings.discordListingWebhookMentionRoleId.trim();
        if (!mentionRoleId.isEmpty()) {
            body.addProperty("content", "<@&" + mentionRoleId + ">");
        }
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        body.add("embeds", embeds);
        post(settings.discordListingWebhookUrl, body);
        return true;
    }

    public boolean sendListingTestMessage() {
        ListingWebhookPayload listing = ListingWebhookPayload.item(
                "WebhookTest",
                "Item",
                "minecraft:diamond",
                "minecraft:diamond",
                16,
                "Test Diamond Bundle",
                12500.0D,
                "test-listing",
                "24h"
        );
        return notifyListingCreated(listing);
    }

    public PriceChangeSections selectSections(List<PriceChange> changes) {
        SettingsManager.Settings settings = AdminShopMod.SETTINGS_MANAGER.settings();
        double minChange = settings.discordWebhookMinChangePercent;
        int maxItems = Math.max(1, settings.discordWebhookMaxItemsPerSection);
        List<PriceChange> increased = changes.stream()
                .filter(change -> change.percentChange() >= minChange)
                .sorted(Comparator.comparingDouble((PriceChange change) -> Math.abs(change.multiplier() - 1.0D)).reversed())
                .limit(maxItems)
                .toList();
        List<PriceChange> decreased = changes.stream()
                .filter(change -> change.percentChange() <= -minChange)
                .sorted(Comparator.comparingDouble((PriceChange change) -> Math.abs(change.multiplier() - 1.0D)).reversed())
                .limit(maxItems)
                .toList();
        return new PriceChangeSections(increased, decreased);
    }

    public List<String> previewLines(List<PriceChange> changes) {
        PriceChangeSections sections = selectSections(changes);
        return List.of(
                "\uD83D\uDCC8 Th\u1ecb tr\u01b0\u1eddng t\u0103ng",
                formatSection(sections.increased()),
                "\uD83D\uDCC9 Th\u1ecb tr\u01b0\u1eddng gi\u1ea3m",
                formatSection(sections.decreased())
        );
    }

    private void post(String webhookUrl, JsonObject body) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();
        } catch (IllegalArgumentException e) {
            AdminShopMod.LOGGER.warn("AdminShop Discord webhook URL is invalid");
            return;
        }

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        AdminShopMod.LOGGER.warn("AdminShop Discord webhook failed with HTTP {}", response.statusCode());
                    }
                })
                .exceptionally(error -> {
                    AdminShopMod.LOGGER.warn("AdminShop Discord webhook request failed: {}", error.getMessage());
                    return null;
                });
    }

    private static boolean shouldSendListingWebhook(SettingsManager.Settings settings, ListingWebhookPayload listing) {
        if (!Boolean.TRUE.equals(settings.discordListingWebhookEnabled) || settings.discordListingWebhookUrl.isBlank()) {
            return false;
        }
        if (listing == null || listing.price() < settings.discordListingWebhookMinPrice) {
            return false;
        }
        if (listing.type() == ListingType.ITEM && !Boolean.TRUE.equals(settings.discordListingWebhookNotifyItems)) {
            return false;
        }
        return listing.type() != ListingType.POKEMON || Boolean.TRUE.equals(settings.discordListingWebhookNotifyPokemon);
    }

    private static JsonObject listingEmbed(ListingWebhookPayload listing) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "New Market Listing");
        embed.addProperty("color", listingColor(listing));
        embed.addProperty("timestamp", Instant.now().toString());

        JsonArray fields = new JsonArray();
        fields.add(field("Seller", safe(listing.sellerName()), true));
        fields.add(field("Type", listing.type().displayName(), true));
        fields.add(field("Name", safe(listing.name()), true));
        fields.add(field("Price", "$" + PriceFormatter.money(listing.price()), true));
        fields.add(field("Expires In", safe(listing.expiresIn()), true));
        fields.add(field("Listing ID", safe(listing.listingId()), true));

        if (listing.type() == ListingType.ITEM) {
            addOptionalField(fields, "Material", listing.materialOrItemId(), true);
            fields.add(field("Quantity", String.valueOf(Math.max(1, listing.quantity())), true));
            addOptionalField(fields, "Custom Name", listing.customName(), true);
        } else {
            addOptionalField(fields, "Species", listing.pokemonSpecies(), true);
            if (listing.pokemonLevel() > 0) {
                fields.add(field("Level", String.valueOf(listing.pokemonLevel()), true));
            }
            fields.add(field("Shiny", String.valueOf(listing.shiny()), true));
            addOptionalField(fields, "Form/Aspects", listing.pokemonAspects(), false);
        }

        embed.add("fields", fields);
        JsonObject footer = new JsonObject();
        footer.addProperty("text", MARKET_FOOTER);
        embed.add("footer", footer);
        return embed;
    }

    private static int listingColor(ListingWebhookPayload listing) {
        if (listing.type() == ListingType.ITEM) {
            return COLOR_BLUE;
        }
        return listing.shiny() ? COLOR_PINK : COLOR_PURPLE;
    }

    private static JsonObject baseEmbed(String title, String description) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("description", description);
        embed.addProperty("color", COLOR_GOLD);
        return embed;
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        return field;
    }

    private static void addOptionalField(JsonArray fields, String name, String value, boolean inline) {
        if (value != null && !value.isBlank()) {
            fields.add(field(name, value, inline));
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private static String formatSection(List<PriceChange> changes) {
        if (changes.isEmpty()) {
            return "Kh\u00f4ng c\u00f3 thay \u0111\u1ed5i \u0111\u00e1ng k\u1ec3";
        }

        StringBuilder builder = new StringBuilder();
        for (PriceChange change : changes) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(change.displayName())
                    .append(": ")
                    .append(formatMultiplier(change.multiplier()))
                    .append(" (")
                    .append(formatPercent(change.percentChange()))
                    .append(")");
        }
        return builder.toString();
    }

    private static String formatMultiplier(double multiplier) {
        return "x" + String.format(java.util.Locale.ROOT, "%.2f", multiplier);
    }

    private static String formatPercent(double percent) {
        long rounded = Math.round(percent);
        return rounded >= 0 ? "+" + rounded + "%" : rounded + "%";
    }

    public record PriceChange(String id, String displayName, double oldPrice, double newPrice, double multiplier, double percentChange) {
    }

    public record PriceChangeSections(List<PriceChange> increased, List<PriceChange> decreased) {
    }

    public enum ListingType {
        ITEM,
        POKEMON;

        private String displayName() {
            String lower = name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }
    }

    public record ListingWebhookPayload(
            ListingType type,
            String sellerName,
            String name,
            double price,
            String listingId,
            String expiresIn,
            String materialOrItemId,
            int quantity,
            String customName,
            String pokemonSpecies,
            int pokemonLevel,
            boolean shiny,
            String pokemonAspects
    ) {
        public static ListingWebhookPayload item(String sellerName, String name, String materialOrItemId,
                                                 String itemId, int quantity, String customName, double price,
                                                 String listingId, String expiresIn) {
            String displayName = name == null || name.isBlank() ? itemId : name;
            return new ListingWebhookPayload(
                    ListingType.ITEM,
                    sellerName,
                    displayName,
                    price,
                    listingId,
                    expiresIn,
                    materialOrItemId == null || materialOrItemId.isBlank() ? itemId : materialOrItemId,
                    Math.max(1, quantity),
                    customName,
                    null,
                    0,
                    false,
                    null
            );
        }

        public static ListingWebhookPayload pokemon(String sellerName, String species, int level, boolean shiny,
                                                    String aspects, double price, String listingId, String expiresIn) {
            return new ListingWebhookPayload(
                    ListingType.POKEMON,
                    sellerName,
                    species,
                    price,
                    listingId,
                    expiresIn,
                    null,
                    1,
                    null,
                    species,
                    level,
                    shiny,
                    aspects
            );
        }
    }
}
