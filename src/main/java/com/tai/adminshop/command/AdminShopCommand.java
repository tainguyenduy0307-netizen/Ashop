package com.tai.adminshop.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.authlib.GameProfile;
import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.config.PriceWindowManager;
import com.tai.adminshop.config.PurchaseLimitManager;
import com.tai.adminshop.config.PurchaseLimitPeriod;
import com.tai.adminshop.config.SettingsManager;
import com.tai.adminshop.config.CuratedCategoryGenerator;
import com.tai.adminshop.config.DefaultShopGenerator;
import com.tai.adminshop.config.ShopCategory;
import com.tai.adminshop.config.ShopEntry;
import com.tai.adminshop.config.YmlDefaultCategoryGenerator;
import com.tai.adminshop.economy.CobEcoHook;
import com.tai.adminshop.economy.Currency;
import com.tai.adminshop.gui.ShopGui;
import com.tai.adminshop.gui.StoreGui;
import com.tai.adminshop.notification.DiscordWebhookNotifier;
import com.tai.adminshop.service.ShopService;
import com.tai.adminshop.util.ItemStackSerializer;
import com.tai.adminshop.util.PriceFormatter;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminShopCommand {
    private static final long REMOVE_CONFIRM_TIMEOUT_MS = 30_000L;
    private static final String SELL_ALL_PERMISSION = "adminshop.sellall";
    private static final String ECO_GIVE_PERMISSION = "adminshop.eco.give";
    private static final String GEMS_ADMIN_PERMISSION = "ashop.gems.admin";
    private static final String GTS_WEBHOOK_PERMISSION = "adminshop.gts.webhook";
    private static final String LIMIT_PERMISSION = "adminshop.limit";
    private static final String COMMAND_ITEM_PERMISSION = "adminshop.commanditem";
    private static final List<String> CURRENCY_SUGGESTIONS = List.of("money", "gems");
    private static final List<String> PERIOD_SUGGESTIONS = List.of("EIGHT_HOURS", "DAILY", "WEEKLY", "MONTHLY");
    private static final Map<UUID, PendingRemove> PENDING_REMOVES = new HashMap<>();

    private AdminShopCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("ashop")
                .requires(AdminShopCommand::hasAshopRootPermission)
                .then(CommandManager.literal("test")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .executes(AdminShopCommand::test))
                .then(CommandManager.literal("reload")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .executes(AdminShopCommand::reload))
                .then(CommandManager.literal("list")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .executes(AdminShopCommand::list))
                .then(CommandManager.literal("validate")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .executes(AdminShopCommand::validate))
                .then(CommandManager.literal("add")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("buyPrice", DoubleArgumentType.doubleArg(0))
                                        .then(CommandManager.argument("sellPrice", DoubleArgumentType.doubleArg(0))
                                                .executes(context -> add(context, "misc", Currency.DOLLARS))
                                                .then(CommandManager.argument("currency", StringArgumentType.word())
                                                        .suggests((context, builder) -> CommandSource.suggestMatching(CURRENCY_SUGGESTIONS, builder))
                                                        .executes(context -> add(context, "misc", StringArgumentType.getString(context, "currency"))))))
                                .then(CommandManager.argument("category", StringArgumentType.word())
                                        .then(CommandManager.argument("buyPrice", DoubleArgumentType.doubleArg(0))
                                                .then(CommandManager.argument("sellPrice", DoubleArgumentType.doubleArg(0))
                                                        .executes(context -> add(context, StringArgumentType.getString(context, "category"), Currency.DOLLARS))
                                                        .then(CommandManager.argument("currency", StringArgumentType.word())
                                                                .suggests((context, builder) -> CommandSource.suggestMatching(CURRENCY_SUGGESTIONS, builder))
                                                                .executes(context -> add(context, StringArgumentType.getString(context, "category"),
                                                                        StringArgumentType.getString(context, "currency")))))))))
                .then(CommandManager.literal("addinventory")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .then(CommandManager.argument("category", StringArgumentType.word())
                                .then(CommandManager.argument("buyPrice", DoubleArgumentType.doubleArg(0))
                                        .then(CommandManager.argument("sellPrice", DoubleArgumentType.doubleArg(0))
                                                .executes(context -> addInventory(context, Currency.DOLLARS))
                                                .then(CommandManager.argument("currency", StringArgumentType.word())
                                                        .suggests((context, builder) -> CommandSource.suggestMatching(CURRENCY_SUGGESTIONS, builder))
                                                        .executes(context -> addInventory(context, StringArgumentType.getString(context, "currency"))))))))
                .then(CommandManager.literal("addhandticket")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("buyPrice", DoubleArgumentType.doubleArg(0))
                                        .executes(context -> addHandTicket(context, Currency.DOLLARS, null))
                                        .then(CommandManager.argument("currencyOrPeriod", StringArgumentType.word())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(addHandTicketThirdArgSuggestions(), builder))
                                                .executes(context -> addHandTicket(context, StringArgumentType.getString(context, "currencyOrPeriod"), null))
                                                .then(CommandManager.argument("period", StringArgumentType.word())
                                                        .suggests((context, builder) -> CommandSource.suggestMatching(PERIOD_SUGGESTIONS, builder))
                                                        .executes(context -> addHandTicket(context,
                                                                StringArgumentType.getString(context, "currencyOrPeriod"),
                                                                StringArgumentType.getString(context, "period"))))))))
                .then(CommandManager.literal("addhand")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("category", StringArgumentType.word())
                                .then(CommandManager.argument("price", DoubleArgumentType.doubleArg(0))
                                        .executes(context -> addHand(context, Currency.DOLLARS))
                                        .then(CommandManager.argument("currency", StringArgumentType.word())
                                                .suggests((context, builder) -> CommandSource.suggestMatching(CURRENCY_SUGGESTIONS, builder))
                                                .executes(context -> addHand(context, StringArgumentType.getString(context, "currency"))))))))
                .then(CommandManager.literal("addcommand")
                        .requires(AdminShopCommand::hasCommandItemPermission)
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("category", StringArgumentType.word())
                                        .then(CommandManager.argument("displayMaterial", IdentifierArgumentType.identifier())
                                                .then(CommandManager.argument("price", DoubleArgumentType.doubleArg(0))
                                                        .then(CommandManager.argument("currency", StringArgumentType.word())
                                                                .suggests((context, builder) -> CommandSource.suggestMatching(CURRENCY_SUGGESTIONS, builder))
                                                                .then(CommandManager.argument("limit", IntegerArgumentType.integer(1))
                                                                        .then(CommandManager.argument("period", StringArgumentType.word())
                                                                                .suggests((context, builder) -> CommandSource.suggestMatching(PERIOD_SUGGESTIONS, builder))
                                                                                .then(CommandManager.argument("rewardCommand", StringArgumentType.greedyString())
                                                                                        .executes(AdminShopCommand::addCommandItem))))))))))
                .then(CommandManager.literal("price")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("buyPrice", DoubleArgumentType.doubleArg(0))
                                        .then(CommandManager.argument("sellPrice", DoubleArgumentType.doubleArg(0))
                                                .executes(AdminShopCommand::price)))))
                .then(CommandManager.literal("category")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .then(CommandManager.argument("category", StringArgumentType.word())
                                        .executes(AdminShopCommand::category))))
                .then(CommandManager.literal("remove")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .executes(AdminShopCommand::remove)))
                .then(CommandManager.literal("confirmremove")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .executes(AdminShopCommand::confirmRemove)))
                .then(CommandManager.literal("backup")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .executes(AdminShopCommand::backup))
                .then(CommandManager.literal("fixcategories")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .executes(AdminShopCommand::fixCategories))
                .then(CommandManager.literal("fixeconomy")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .executes(AdminShopCommand::fixEconomy))
                .then(CommandManager.literal("generate")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .then(CommandManager.literal("curated")
                                .executes(context -> generateCurated(context, false))
                                .then(CommandManager.literal("force")
                                        .executes(context -> generateCurated(context, true))))
                        .then(CommandManager.literal("from_yml")
                                .then(CommandManager.literal("force")
                                        .executes(AdminShopCommand::generateFromYml)))
                        .then(CommandManager.literal("vanilla_full")
                                .executes(context -> generateVanillaFull(context, false))
                                .then(CommandManager.literal("force")
                                        .executes(context -> generateVanillaFull(context, true)))))
                .then(CommandManager.literal("pricewindow")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .then(CommandManager.literal("reload")
                                .executes(AdminShopCommand::priceWindowReload))
                        .then(CommandManager.literal("reroll")
                                .executes(AdminShopCommand::priceWindowReroll))
                        .then(CommandManager.literal("info")
                                .executes(AdminShopCommand::priceWindowInfoSummary)
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(AdminShopCommand::priceWindowInfo))))
                .then(CommandManager.literal("limit")
                        .requires(AdminShopCommand::hasLimitPermission)
                        .then(CommandManager.literal("set")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .then(CommandManager.argument("limit", IntegerArgumentType.integer(1))
                                                .then(CommandManager.argument("period", StringArgumentType.word())
                                                        .executes(AdminShopCommand::limitSet)))))
                        .then(CommandManager.literal("clear")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(AdminShopCommand::limitClear)))
                        .then(CommandManager.literal("info")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(AdminShopCommand::limitInfo)))
                        .then(CommandManager.literal("reset")
                                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                        .then(CommandManager.argument("id", StringArgumentType.word())
                                                .executes(AdminShopCommand::limitReset)))))
                .then(CommandManager.literal("webhook")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .then(CommandManager.literal("test")
                                .executes(AdminShopCommand::webhookTest))
                        .then(CommandManager.literal("reload")
                                .executes(AdminShopCommand::webhookReload))
                        .then(CommandManager.literal("preview")
                                .executes(AdminShopCommand::webhookPreview)))
                .then(CommandManager.literal("clearpokemon")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .executes(AdminShopCommand::clearCobblemon))
                .then(CommandManager.literal("clearcobblemon")
                        .requires(AdminShopCommand::hasAdminPermission)
                        .executes(AdminShopCommand::clearCobblemon)));

        dispatcher.register(CommandManager.literal("shop")
                .executes(AdminShopCommand::openShop));

        dispatcher.register(CommandManager.literal("store")
                .executes(AdminShopCommand::openStore));

        dispatcher.register(CommandManager.literal("gems")
                .executes(AdminShopCommand::gemsSelf)
                .then(CommandManager.literal("give")
                        .requires(AdminShopCommand::hasGemsAdminPermission)
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(context -> gemsModify(context, "give")))))
                .then(CommandManager.literal("take")
                        .requires(AdminShopCommand::hasGemsAdminPermission)
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(context -> gemsModify(context, "take")))))
                .then(CommandManager.literal("set")
                        .requires(AdminShopCommand::hasGemsAdminPermission)
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(context -> gemsModify(context, "set")))))
                .then(CommandManager.literal("balance")
                        .requires(AdminShopCommand::hasGemsAdminPermission)
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                .executes(AdminShopCommand::gemsBalance))));

        dispatcher.register(CommandManager.literal("sell")
                .then(CommandManager.literal("hand")
                        .executes(AdminShopCommand::sellHand))
                .then(CommandManager.literal("all")
                        .executes(AdminShopCommand::sellAll)));

        dispatcher.register(CommandManager.literal("eco")
                .then(CommandManager.literal("give")
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(AdminShopCommand::ecoGive)))));

        dispatcher.register(CommandManager.literal("gts")
                .then(CommandManager.literal("webhook")
                        .requires(AdminShopCommand::hasGtsWebhookPermission)
                        .then(CommandManager.literal("test")
                                .executes(AdminShopCommand::listingWebhookTest))));

        dispatcher.register(CommandManager.literal("market")
                .then(CommandManager.literal("webhook")
                        .requires(AdminShopCommand::hasGtsWebhookPermission)
                        .then(CommandManager.literal("test")
                                .executes(AdminShopCommand::listingWebhookTest))));
    }

    private static int test(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(() -> Text.literal("AdminShop loaded"), false);
        return 1;
    }

    private static int reload(CommandContext<ServerCommandSource> context) {
        AdminShopMod.SHOP_MANAGER.load();
        AdminShopMod.STORE_MANAGER.load();
        AdminShopMod.GEMS_MANAGER.load();
        AdminShopMod.SETTINGS_MANAGER.reload();
        AdminShopMod.PRICE_WINDOW_MANAGER.reload();
        AdminShopMod.PURCHASE_LIMIT_MANAGER.load();
        context.getSource().sendFeedback(() -> Text.literal("AdminShop reloaded"), true);
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Collection<ShopEntry> entries = AdminShopMod.SHOP_MANAGER.all();

        if (entries.isEmpty()) {
            source.sendFeedback(() -> Text.literal("AdminShop has no items"), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("AdminShop items:"), false);
        for (ShopEntry entry : entries) {
            source.sendFeedback(() -> Text.literal("[" + ShopEntry.normalizeCategory(entry.category) + "] " + entry.id
                    + " buy=" + PriceFormatter.money(entry.buyPrice) + " sell=" + PriceFormatter.money(entry.sellPrice)), false);
        }
        return entries.size();
    }

    private static int validate(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        List<String> report = AdminShopMod.SHOP_MANAGER.validateCategories();
        if (report.isEmpty()) {
            source.sendFeedback(() -> Text.literal("AdminShop validation passed."), true);
            AdminShopMod.LOGGER.info("[AdminShop] Category validation passed.");
            return 1;
        }

        AdminShopMod.LOGGER.warn("[AdminShop] Category validation found {} issue(s):", report.size());
        for (String line : report) {
            AdminShopMod.LOGGER.warn("[AdminShop] - {}", line);
        }

        source.sendFeedback(() -> Text.literal("AdminShop validation found " + report.size() + " issue(s). Showing first 50:"), false);
        int limit = Math.min(50, report.size());
        for (int i = 0; i < limit; i++) {
            String line = report.get(i);
            source.sendFeedback(() -> Text.literal("- " + line), false);
        }
        if (report.size() > limit) {
            source.sendFeedback(() -> Text.literal("... " + (report.size() - limit) + " more issue(s) in server log."), false);
        }
        return report.size();
    }

    private static int add(CommandContext<ServerCommandSource> context, String categoryArgument, String currencyValue) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String id = StringArgumentType.getString(context, "id");
        String category = resolveCategoryId(source, categoryArgument);
        if (category == null) {
            return 0;
        }
        String currency = Currency.normalize(currencyValue);
        double buyPrice = DoubleArgumentType.getDouble(context, "buyPrice");
        double sellPrice = DoubleArgumentType.getDouble(context, "sellPrice");
        ItemStack held = player.getInventory().getMainHandStack();

        if (held.isEmpty()) {
            source.sendError(Text.literal("You must hold an item in your main hand"));
            return 0;
        }

        String itemData = ItemStackSerializer.serialize(held, source.getRegistryManager());
        ShopEntry entry = new ShopEntry(id, category, buyPrice, sellPrice, itemData);
        entry.material = Registries.ITEM.getId(held.getItem()).toString();
        entry.currency = currency;
        entry.quantity = 1;
        entry.page = 1;
        entry.slot = -1;
        AdminShopMod.SHOP_MANAGER.addOrReplace(entry);
        source.sendFeedback(() -> Text.literal("Added shop item '" + id + "' category="
                + category + " buy=" + Currency.format(currency, buyPrice)
                + " sell=" + Currency.format(currency, sellPrice)), true);
        return 1;
    }

    private static int remove(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            return removeNow(source, id);
        }

        if (AdminShopMod.SHOP_MANAGER.get(id).isEmpty()) {
            source.sendError(Text.literal("Unknown shop item: " + id));
            return 0;
        }

        PENDING_REMOVES.put(player.getUuid(), new PendingRemove(id, System.currentTimeMillis() + REMOVE_CONFIRM_TIMEOUT_MS));
        source.sendFeedback(() -> Text.literal("Gõ /ashop confirmremove " + id + " trong 30 giây để xác nhận."), false);
        return 1;
    }

    private static int confirmRemove(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            return removeNow(source, id);
        }

        PendingRemove pending = PENDING_REMOVES.get(player.getUuid());
        if (pending == null || System.currentTimeMillis() > pending.expiresAt || !pending.id.equalsIgnoreCase(id)) {
            PENDING_REMOVES.remove(player.getUuid());
            source.sendError(Text.literal("Khong co lenh remove dang cho xac nhan hoac da het han: " + id));
            return 0;
        }

        PENDING_REMOVES.remove(player.getUuid());
        return removeNow(source, id);
    }

    private static int removeNow(ServerCommandSource source, String id) {
        if (!AdminShopMod.SHOP_MANAGER.remove(id)) {
            source.sendError(Text.literal("Unknown shop item: " + id));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Removed shop item '" + id + "'"), true);
        return 1;
    }

    private static int addInventory(CommandContext<ServerCommandSource> context, String currencyValue) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String categoryArgument = StringArgumentType.getString(context, "category");
        String category = resolveCategoryId(source, categoryArgument);
        if (category == null) {
            return 0;
        }
        String currency = Currency.normalize(currencyValue);
        double buyPrice = DoubleArgumentType.getDouble(context, "buyPrice");
        double sellPrice = DoubleArgumentType.getDouble(context, "sellPrice");
        int added = 0;

        for (ItemStack stack : player.getInventory().main) {
            if (stack.isEmpty()) {
                continue;
            }

            String id = Registries.ITEM.getId(stack.getItem()).getPath();
            String itemData = ItemStackSerializer.serialize(stack, source.getRegistryManager());
            ShopEntry entry = new ShopEntry(id, category, buyPrice, sellPrice, itemData);
            entry.material = Registries.ITEM.getId(stack.getItem()).toString();
            entry.currency = currency;
            entry.quantity = 1;
            entry.page = 1;
            entry.slot = -1;
            AdminShopMod.SHOP_MANAGER.addOrReplace(entry);
            added++;
        }

        int total = added;
        source.sendFeedback(() -> Text.literal("Added/replaced " + total + " shop items from inventory category="
                + category + " buy=" + Currency.format(currency, buyPrice)
                + " sell=" + Currency.format(currency, sellPrice)), true);
        return added;
    }

    private static int addHandTicket(CommandContext<ServerCommandSource> context, String currencyOrPeriod, String periodValue) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String id = StringArgumentType.getString(context, "id");
        double buyPrice = DoubleArgumentType.getDouble(context, "buyPrice");
        ItemStack held = player.getInventory().getMainHandStack();

        if (held.isEmpty()) {
            source.sendError(Text.literal("You must hold a ticket item in your main hand"));
            return 0;
        }

        String currency = Currency.DOLLARS;
        PurchaseLimitPeriod period = PurchaseLimitPeriod.NONE;
        if (periodValue != null) {
            currency = Currency.normalize(currencyOrPeriod);
            try {
                period = PurchaseLimitPeriod.parseStrict(periodValue);
            } catch (IllegalArgumentException e) {
                source.sendError(Text.literal(e.getMessage()));
                return 0;
            }
        } else if (currencyOrPeriod != null) {
            try {
                period = PurchaseLimitPeriod.parseStrict(currencyOrPeriod);
            } catch (IllegalArgumentException ignored) {
                currency = Currency.normalize(currencyOrPeriod);
            }
        }

        if (period == PurchaseLimitPeriod.NONE && periodValue != null) {
            source.sendError(Text.literal("Use EIGHT_HOURS, DAILY, WEEKLY, or MONTHLY for limited tickets."));
            return 0;
        }
        String itemData = ItemStackSerializer.serialize(held, source.getRegistryManager());
        ShopEntry entry = new ShopEntry(id, "tickets", buyPrice, 0.0D, itemData);
        entry.material = Registries.ITEM.getId(held.getItem()).toString();
        entry.currency = currency;
        entry.quantity = 1;
        entry.page = 1;
        entry.slot = -1;
        entry.dynamicPricing = false;
        if (period != PurchaseLimitPeriod.NONE) {
            entry.purchaseLimitEnabled = true;
            entry.purchaseLimit = 1;
            entry.purchaseLimitPeriod = period.name();
            entry.purchaseLimitLabel = period.defaultLabel(1);
        }

        AdminShopMod.SHOP_MANAGER.addOrReplace(entry);
        String limitMessage = period == PurchaseLimitPeriod.NONE ? "" : " limit=" + entry.purchaseLimitLabel;
        String message = "Added ticket '" + id + "' buy=" + Currency.format(currency, buyPrice) + limitMessage;
        source.sendFeedback(() -> Text.literal(message), true);
        return 1;
    }

    private static int addHand(CommandContext<ServerCommandSource> context, String currencyValue) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String id = StringArgumentType.getString(context, "id");
        String categoryArgument = StringArgumentType.getString(context, "category");
        String category = resolveCategoryId(source, categoryArgument);
        if (category == null) {
            return 0;
        }
        double price = DoubleArgumentType.getDouble(context, "price");
        String currency = Currency.normalize(currencyValue);
        ItemStack held = player.getInventory().getMainHandStack();

        if (held.isEmpty()) {
            source.sendError(Text.literal("You must hold an item in your main hand"));
            return 0;
        }

        String itemData = ItemStackSerializer.serialize(held, source.getRegistryManager());
        String material = Registries.ITEM.getId(held.getItem()).toString();

        ShopEntry entry = new ShopEntry(id, category, price, 0.0D, itemData);
        entry.material = material;
        entry.currency = currency;
        entry.quantity = Math.max(1, held.getCount());
        entry.page = 1;
        entry.slot = -1;
        entry.dynamicPricing = false;
        AdminShopMod.SHOP_MANAGER.addOrReplace(entry);
        source.sendFeedback(() -> Text.literal("Added hand item to category=" + category + " id=" + entry.id + " buy="
                + Currency.format(currency, price)), true);
        return 1;
    }

    private static int addCommandItem(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        String categoryArgument = StringArgumentType.getString(context, "category");
        String category = resolveCategoryId(source, categoryArgument);
        if (category == null) {
            return 0;
        }

        Identifier materialId = IdentifierArgumentType.getIdentifier(context, "displayMaterial");
        if (!Registries.ITEM.containsId(materialId)) {
            source.sendError(Text.literal("Invalid display material: " + materialId));
            return 0;
        }

        double price = DoubleArgumentType.getDouble(context, "price");
        String currency = Currency.normalize(StringArgumentType.getString(context, "currency"));
        int limit = IntegerArgumentType.getInteger(context, "limit");
        PurchaseLimitPeriod period;
        try {
            period = PurchaseLimitPeriod.parseStrict(StringArgumentType.getString(context, "period"));
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal(e.getMessage()));
            return 0;
        }
        if (period == PurchaseLimitPeriod.NONE) {
            source.sendError(Text.literal("Use EIGHT_HOURS, DAILY, WEEKLY, or MONTHLY for enabled purchase limits."));
            return 0;
        }

        String rewardCommand = unquoteCommand(StringArgumentType.getString(context, "rewardCommand").trim());
        if (rewardCommand.isBlank()) {
            source.sendError(Text.literal("rewardCommand cannot be empty"));
            return 0;
        }
        if (rewardCommand.startsWith("/")) {
            rewardCommand = rewardCommand.substring(1).trim();
        }
        if (rewardCommand.isBlank()) {
            source.sendError(Text.literal("rewardCommand cannot be empty"));
            return 0;
        }

        ShopEntry entry = new ShopEntry(id, category, price, 0.0D, null);
        entry.material = materialId.toString();
        entry.quantity = 1;
        entry.currency = currency;
        entry.rewardType = "COMMAND";
        entry.rewardCommand = rewardCommand;
        entry.dynamicPricing = false;
        entry.page = 1;
        entry.slot = -1;
        entry.purchaseLimitEnabled = true;
        entry.purchaseLimit = limit;
        entry.purchaseLimitPeriod = period.name();
        entry.purchaseLimitLabel = vietnameseLimitLabel(limit, period);

        AdminShopMod.SHOP_MANAGER.addOrReplace(entry);
        String message = "Added command item '" + id + "' category=" + category + " buy="
                + Currency.format(currency, price) + " limit=" + entry.purchaseLimitLabel;
        source.sendFeedback(() -> Text.literal(message), true);
        return 1;
    }

    private static int price(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        double buyPrice = DoubleArgumentType.getDouble(context, "buyPrice");
        double sellPrice = DoubleArgumentType.getDouble(context, "sellPrice");

        if (!AdminShopMod.SHOP_MANAGER.updatePrice(id, buyPrice, sellPrice)) {
            source.sendError(Text.literal("Unknown shop item: " + id));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Updated price for '" + id + "' buy=" + PriceFormatter.money(buyPrice)
                + " sell=" + PriceFormatter.money(sellPrice)), true);
        return 1;
    }

    private static int category(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        String categoryArgument = StringArgumentType.getString(context, "category");
        String category = resolveCategoryId(source, categoryArgument);
        if (category == null) {
            return 0;
        }

        if (!AdminShopMod.SHOP_MANAGER.updateCategory(id, category)) {
            source.sendError(Text.literal("Unknown shop item: " + id));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Moved '" + id + "' to category=" + category), true);
        return 1;
    }

    private static int limitSet(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        int limit = IntegerArgumentType.getInteger(context, "limit");
        PurchaseLimitPeriod period;
        try {
            period = PurchaseLimitPeriod.parseStrict(StringArgumentType.getString(context, "period"));
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal(e.getMessage()));
            return 0;
        }
        if (period == PurchaseLimitPeriod.NONE) {
            source.sendError(Text.literal("Use EIGHT_HOURS, DAILY, WEEKLY, or MONTHLY for enabled purchase limits."));
            return 0;
        }

        String label = period.defaultLabel(limit);
        if (!AdminShopMod.SHOP_MANAGER.updatePurchaseLimit(id, true, limit, period, label)) {
            source.sendError(Text.literal("Unknown shop item: " + id));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Set purchase limit for '" + id + "': " + label), true);
        return 1;
    }

    private static int limitClear(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");

        if (!AdminShopMod.SHOP_MANAGER.updatePurchaseLimit(id, false, 0, PurchaseLimitPeriod.NONE, null)) {
            source.sendError(Text.literal("Unknown shop item: " + id));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Cleared purchase limit for '" + id + "'"), true);
        return 1;
    }

    private static int limitInfo(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        ShopEntry entry = AdminShopMod.SHOP_MANAGER.get(id).orElse(null);
        if (entry == null) {
            source.sendError(Text.literal("Unknown shop item: " + id));
            return 0;
        }

        if (!PurchaseLimitManager.isLimited(entry)) {
            source.sendFeedback(() -> Text.literal("'" + id + "' has no purchase limit."), false);
            return 1;
        }

        PurchaseLimitPeriod period = PurchaseLimitManager.period(entry);
        PurchaseLimitPeriod.Window window = period.currentWindow();
        source.sendFeedback(() -> Text.literal("Purchase limit for '" + id + "':"), false);
        source.sendFeedback(() -> Text.literal("Limit: " + entry.purchaseLimit), false);
        source.sendFeedback(() -> Text.literal("Period: " + period.name()), false);
        source.sendFeedback(() -> Text.literal("Label: " + PurchaseLimitManager.label(entry)), false);
        source.sendFeedback(() -> Text.literal("Current Window: " + window.key()), false);
        source.sendFeedback(() -> Text.literal("Resets in: " + PurchaseLimitManager.formatDuration(window.timeUntilReset())), false);
        return 1;
    }

    private static int limitReset(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        if (AdminShopMod.SHOP_MANAGER.get(id).isEmpty()) {
            source.sendError(Text.literal("Unknown shop item: " + id));
            return 0;
        }

        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "player");
        int reset = 0;
        for (GameProfile profile : profiles) {
            UUID uuid = profile.getId();
            if (uuid == null) {
                source.sendError(Text.literal("Unknown player UUID for " + profile.getName()));
                continue;
            }
            if (AdminShopMod.PURCHASE_LIMIT_MANAGER.reset(uuid, id)) {
                reset++;
            }
        }

        int total = reset;
        source.sendFeedback(() -> Text.literal("Reset purchase limit data for " + total + " player(s) on '" + id + "'."), true);
        return reset;
    }

    private static int backup(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        try {
            Path backup = AdminShopMod.SHOP_MANAGER.backup();
            source.sendFeedback(() -> Text.literal("Backup created: " + backup), true);
            return 1;
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to backup AdminShop config", e);
            source.sendError(Text.literal("Failed to backup shops.json: " + e.getMessage()));
            return 0;
        }
    }

    private static int generateVanillaFull(CommandContext<ServerCommandSource> context, boolean force) {
        ServerCommandSource source = context.getSource();
        if (!force && !AdminShopMod.SHOP_MANAGER.isEmpty()) {
            source.sendError(Text.literal("Shop is not empty. Use /ashop generate vanilla_full force to replace vanilla shop."));
            return 0;
        }

        Path backupPath = null;
        if (force) {
            try {
                backupPath = AdminShopMod.SHOP_MANAGER.backup();
            } catch (IOException e) {
                AdminShopMod.LOGGER.error("Failed to backup AdminShop config before vanilla_full generation", e);
                source.sendError(Text.literal("Failed to backup shops.json: " + e.getMessage()));
                return 0;
            }
        }

        Collection<ShopEntry> generated = DefaultShopGenerator.generate();
        AdminShopMod.SHOP_MANAGER.replaceWithVanillaFullKeepingTickets(generated);
        AdminShopMod.PRICE_WINDOW_MANAGER.reload();
        int count = generated.size();
        Path backup = backupPath;
        source.sendFeedback(() -> Text.literal("Generated vanilla_full shop with " + count + " vanilla items."
                + (backup == null ? "" : " Backup: " + backup)), true);
        return count;
    }

    private static int generateCurated(CommandContext<ServerCommandSource> context, boolean force) {
        ServerCommandSource source = context.getSource();
        try {
            int count = CuratedCategoryGenerator.generate(force);
            AdminShopMod.SHOP_MANAGER.load();
            AdminShopMod.PRICE_WINDOW_MANAGER.reload();
            source.sendFeedback(() -> Text.literal("Generated curated category JSON with " + count + " items."), true);
            return count;
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to generate curated AdminShop categories", e);
            source.sendError(Text.literal("Failed to generate curated categories: " + e.getMessage()));
            return 0;
        }
    }

    private static int generateFromYml(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        try {
            int count = YmlDefaultCategoryGenerator.generate(true);
            AdminShopMod.SHOP_MANAGER.load();
            AdminShopMod.PRICE_WINDOW_MANAGER.reload();
            source.sendFeedback(() -> Text.literal("Generated category JSON from YAML defaults with " + count + " items."), true);
            return count;
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to generate AdminShop categories from YAML defaults", e);
            source.sendError(Text.literal("Failed to generate from YAML defaults: " + e.getMessage()));
            return 0;
        }
    }

    private static int fixCategories(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Path backup;
        try {
            backup = AdminShopMod.SHOP_MANAGER.backup();
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to backup AdminShop config before category fix", e);
            source.sendError(Text.literal("Failed to backup shops.json: " + e.getMessage()));
            return 0;
        }

        int fixed = AdminShopMod.SHOP_MANAGER.fixVanillaCategories();
        AdminShopMod.PRICE_WINDOW_MANAGER.reload();
        source.sendFeedback(() -> Text.literal("Fixed categories for " + fixed + " vanilla items. Backup: " + backup), true);
        return fixed;
    }

    private static int fixEconomy(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Path backup;
        try {
            backup = AdminShopMod.SHOP_MANAGER.backupCategories();
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to backup AdminShop categories before economy fix", e);
            source.sendError(Text.literal("Failed to backup categories: " + e.getMessage()));
            return 0;
        }

        int fixed = AdminShopMod.SHOP_MANAGER.fixEconomy();
        AdminShopMod.PRICE_WINDOW_MANAGER.reload();
        source.sendFeedback(() -> Text.literal("Fixed economy data for " + fixed + " shop items. Backup: " + backup), true);
        return fixed;
    }

    private static int priceWindowReload(CommandContext<ServerCommandSource> context) {
        AdminShopMod.PRICE_WINDOW_MANAGER.reload();
        context.getSource().sendFeedback(() -> Text.literal("Price window reloaded: " + AdminShopMod.PRICE_WINDOW_MANAGER.windowKey()), true);
        return 1;
    }

    private static int priceWindowReroll(CommandContext<ServerCommandSource> context) {
        AdminShopMod.PRICE_WINDOW_MANAGER.reroll();
        context.getSource().sendFeedback(() -> Text.literal("Price window rerolled: " + AdminShopMod.PRICE_WINDOW_MANAGER.windowKey()), true);
        return 1;
    }

    private static int priceWindowInfo(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String id = StringArgumentType.getString(context, "id");
        ShopEntry entry = AdminShopMod.SHOP_MANAGER.get(id).orElse(null);
        if (entry == null) {
            source.sendError(Text.literal("Unknown shop item: " + id));
            return 0;
        }

        PriceWindowManager.PriceWindowInfo info = AdminShopMod.PRICE_WINDOW_MANAGER.info(entry);
        source.sendFeedback(() -> Text.literal("Price window for '" + id + "' windowKey=" + info.windowKey()), false);
        source.sendFeedback(() -> Text.literal("Price Group: " + info.priceGroup()), false);
        source.sendFeedback(() -> Text.literal("Dynamic: " + info.dynamic() + " multiplier=" + formatMultiplier(info.multiplier())
                + " (" + formatPercent(info.multiplier()) + ")"), false);
        source.sendFeedback(() -> Text.literal("Base Buy: " + PriceFormatter.money(info.baseBuy())
                + " Current Buy: " + PriceFormatter.money(info.currentBuy())), false);
        source.sendFeedback(() -> Text.literal("Base Sell: " + PriceFormatter.money(info.baseSell())
                + " Current Sell: " + PriceFormatter.money(info.currentSell())), false);
        source.sendFeedback(() -> Text.literal("Price Window: " + info.windowName() + " Next Update: " + info.nextUpdate()), false);
        return 1;
    }

    private static int priceWindowInfoSummary(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        SettingsManager.Settings settings = AdminShopMod.SETTINGS_MANAGER.settings();
        source.sendFeedback(() -> Text.literal("Current Window: " + AdminShopMod.PRICE_WINDOW_MANAGER.windowKey()), false);
        source.sendFeedback(() -> Text.literal("Next Update: " + AdminShopMod.PRICE_WINDOW_MANAGER.nextUpdateLabel()), false);
        source.sendFeedback(() -> Text.literal("Dynamic Pricing Enabled: " + Boolean.TRUE.equals(settings.dynamicPricingEnabled)), false);
        source.sendFeedback(() -> Text.literal("Webhook Enabled: " + Boolean.TRUE.equals(settings.discordWebhookEnabled)), false);
        source.sendFeedback(() -> Text.literal("Dynamic Group Count: " + AdminShopMod.PRICE_WINDOW_MANAGER.dynamicGroupsCount()), false);
        source.sendFeedback(() -> Text.literal("Dynamic Items Count: " + AdminShopMod.PRICE_WINDOW_MANAGER.dynamicItemsCount()), false);
        return 1;
    }

    private static int webhookTest(CommandContext<ServerCommandSource> context) {
        boolean queued = AdminShopMod.DISCORD_WEBHOOK_NOTIFIER.sendTestMessage();
        if (!queued) {
            context.getSource().sendError(Text.literal("Discord webhook is disabled or URL is empty."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("Discord webhook test queued."), false);
        return 1;
    }

    private static int webhookReload(CommandContext<ServerCommandSource> context) {
        AdminShopMod.SETTINGS_MANAGER.reload();
        context.getSource().sendFeedback(() -> Text.literal("Discord webhook settings reloaded."), true);
        return 1;
    }

    private static int webhookPreview(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        List<DiscordWebhookNotifier.PriceChange> changes = AdminShopMod.PRICE_WINDOW_MANAGER.previewRerollChanges();
        for (String line : AdminShopMod.DISCORD_WEBHOOK_NOTIFIER.previewLines(changes)) {
            source.sendFeedback(() -> Text.literal(line), false);
        }
        return 1;
    }

    private static int listingWebhookTest(CommandContext<ServerCommandSource> context) {
        boolean queued = AdminShopMod.DISCORD_WEBHOOK_NOTIFIER.sendListingTestMessage();
        if (!queued) {
            context.getSource().sendError(Text.literal("GTS listing webhook is disabled, URL is empty, or filters skipped the test listing."));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal("GTS listing webhook test queued."), false);
        return 1;
    }

    private static int clearCobblemon(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Path backup;
        try {
            backup = AdminShopMod.SHOP_MANAGER.backup();
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to backup AdminShop config before Cobblemon cleanup", e);
            source.sendError(Text.literal("Failed to backup shops.json before cleanup: " + e.getMessage()));
            return 0;
        }

        int removed = 0;
        for (ShopEntry entry : AdminShopMod.SHOP_MANAGER.all()) {
            if (entry.id == null || !shouldRemoveCobblemonEntry(source, entry)) {
                continue;
            }
            if (AdminShopMod.SHOP_MANAGER.remove(entry.id)) {
                removed++;
            }
        }

        int total = removed;
        Path backupPath = backup;
        source.sendFeedback(() -> Text.literal("Removed " + total + " Cobblemon shop items. Backup: " + backupPath), true);
        return removed;
    }

    private static boolean shouldRemoveCobblemonEntry(ServerCommandSource source, ShopEntry entry) {
        ItemStack stack;
        try {
            stack = ItemStackSerializer.deserializeEntry(entry, source.getRegistryManager());
        } catch (Exception e) {
            AdminShopMod.LOGGER.error("Failed to inspect shop item {} during Cobblemon cleanup", entry.id, e);
            return false;
        }

        if (stack.isEmpty() || !"cobblemon".equals(Registries.ITEM.getId(stack.getItem()).getNamespace())) {
            return false;
        }

        String category = ShopEntry.normalizeCategory(entry.category);
        return !"tickets".equals(category) && !"crates".equals(category);
    }

    private static int openShop(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        ShopGui.openMainMenu(player);
        return 1;
    }

    private static int openStore(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        StoreGui.open(player);
        return 1;
    }

    private static int gemsSelf(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        double balance = AdminShopMod.GEMS_MANAGER.balance(player.getUuid());
        player.sendMessage(Text.literal("Gems: " + PriceFormatter.integer(balance)), false);
        return 1;
    }

    private static int gemsBalance(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "player");
        int count = 0;
        for (GameProfile profile : profiles) {
            UUID uuid = profile.getId();
            if (uuid == null) {
                source.sendError(Text.literal("Unknown player UUID for " + profile.getName()));
                continue;
            }
            double balance = AdminShopMod.GEMS_MANAGER.balance(uuid);
            source.sendFeedback(() -> Text.literal(profile.getName() + " Gems: " + PriceFormatter.integer(balance)), false);
            count++;
        }
        return count;
    }

    private static int gemsModify(CommandContext<ServerCommandSource> context, String action) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "player");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        int changed = 0;

        for (GameProfile profile : profiles) {
            UUID uuid = profile.getId();
            if (uuid == null) {
                source.sendError(Text.literal("Unknown player UUID for " + profile.getName()));
                continue;
            }

            boolean ok = switch (action) {
                case "give" -> AdminShopMod.GEMS_MANAGER.give(uuid, amount);
                case "take" -> AdminShopMod.GEMS_MANAGER.take(uuid, amount);
                case "set" -> AdminShopMod.GEMS_MANAGER.set(uuid, amount);
                default -> false;
            };
            if (!ok) {
                source.sendError(Text.literal("Could not " + action + " gems for " + profile.getName()));
                continue;
            }

            double balance = AdminShopMod.GEMS_MANAGER.balance(uuid);
            source.sendFeedback(() -> Text.literal(action + " " + PriceFormatter.integer(amount) + " Gems for "
                    + profile.getName() + ". Balance: " + PriceFormatter.integer(balance)), true);
            ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(uuid);
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage(Text.literal("Your Gems balance is now " + PriceFormatter.integer(balance)), false);
            }
            changed++;
        }
        return changed;
    }

    private static int sellHand(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        return ShopService.sellHand(player) ? 1 : 0;
    }

    private static int sellAll(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!hasSellAllPermission(context.getSource())) {
            context.getSource().sendError(Text.literal("You do not have permission to use /sell all."));
            return 0;
        }
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        return ShopService.sellAll(player) ? 1 : 0;
    }

    private static int ecoGive(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        if (!hasEcoGivePermission(source)) {
            source.sendError(Text.literal("You do not have permission to use this command."));
            return 0;
        }

        Collection<GameProfile> profiles = GameProfileArgumentType.getProfileArgument(context, "player");
        double amount = DoubleArgumentType.getDouble(context, "amount");
        int given = 0;

        for (GameProfile profile : profiles) {
            UUID uuid = profile.getId();
            if (uuid == null) {
                source.sendError(Text.literal("Unknown player UUID for " + profile.getName()));
                continue;
            }
            if (!CobEcoHook.giveMoney(uuid, amount)) {
                source.sendError(Text.literal("Could not give money to " + profile.getName() + "."));
                continue;
            }

            String money = PriceFormatter.money(amount);
            source.sendFeedback(() -> Text.literal("Gave " + money + " to " + profile.getName() + "."), true);
            ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(uuid);
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage(Text.literal("You received " + money + "."), false);
            }
            given++;
        }

        return given;
    }

    private static String resolveCategoryId(ServerCommandSource source, String category) {
        return ShopCategory.resolve(category)
                .map(ShopCategory::id)
                .orElseGet(() -> {
                    source.sendError(Text.literal("Category not found: " + category));
                    return null;
                });
    }

    private static String unquoteCommand(String command) {
        if (command.length() < 2) {
            return command;
        }
        char first = command.charAt(0);
        char last = command.charAt(command.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return command.substring(1, command.length() - 1).trim();
        }
        return command;
    }

    private static List<String> addHandTicketThirdArgSuggestions() {
        return List.of("money", "gems", "EIGHT_HOURS", "DAILY", "WEEKLY", "MONTHLY");
    }

    private static String vietnameseLimitLabel(int limit, PurchaseLimitPeriod period) {
        return switch (period) {
            case EIGHT_HOURS -> limit + " lần mỗi 8 giờ";
            case DAILY -> limit + " lần mỗi ngày";
            case WEEKLY -> limit + " lần mỗi tuần";
            case MONTHLY -> limit + " lần mỗi tháng";
            case NONE -> "";
        };
    }

    private static boolean hasSellAllPermission(ServerCommandSource source) {
        return hasPermission(source, SELL_ALL_PERMISSION);
    }

    private static boolean hasEcoGivePermission(ServerCommandSource source) {
        return source.getEntity() == null || hasPermission(source, ECO_GIVE_PERMISSION);
    }

    private static boolean hasGtsWebhookPermission(ServerCommandSource source) {
        return hasPermission(source, GTS_WEBHOOK_PERMISSION);
    }

    private static boolean hasGemsAdminPermission(ServerCommandSource source) {
        return hasPermission(source, GEMS_ADMIN_PERMISSION);
    }

    private static boolean hasCommandItemPermission(ServerCommandSource source) {
        return hasPermission(source, COMMAND_ITEM_PERMISSION);
    }

    private static boolean hasLimitPermission(ServerCommandSource source) {
        return hasPermission(source, LIMIT_PERMISSION);
    }

    private static boolean hasAdminPermission(ServerCommandSource source) {
        return source.hasPermissionLevel(2);
    }

    private static boolean hasAshopRootPermission(ServerCommandSource source) {
        return hasAdminPermission(source) || hasLimitPermission(source) || hasCommandItemPermission(source);
    }

    private static boolean hasPermission(ServerCommandSource source, String permission) {
        return source.hasPermissionLevel(2) || checkFabricPermission(source, permission);
    }

    private static boolean checkFabricPermission(ServerCommandSource source, String permission) {
        try {
            Class<?> permissions = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            try {
                java.lang.reflect.Method check = permissions.getMethod("check", ServerCommandSource.class, String.class, int.class);
                Object result = check.invoke(null, source, permission, 2);
                return Boolean.TRUE.equals(result);
            } catch (NoSuchMethodException ignored) {
                java.lang.reflect.Method check = permissions.getMethod("check", ServerCommandSource.class, String.class);
                Object result = check.invoke(null, source, permission);
                return Boolean.TRUE.equals(result);
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    private record PendingRemove(String id, long expiresAt) {
    }

    private static String formatMultiplier(double multiplier) {
        return String.format(java.util.Locale.ROOT, "%.3f", multiplier);
    }

    private static String formatPercent(double multiplier) {
        long percent = Math.round((multiplier - 1.0D) * 100.0D);
        return percent >= 0 ? "+" + percent + "%" : percent + "%";
    }
}

