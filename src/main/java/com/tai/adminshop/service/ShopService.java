package com.tai.adminshop.service;

import com.tai.adminshop.AdminShopMod;
import com.mojang.brigadier.ParseResults;
import com.tai.adminshop.config.ShopEntry;
import com.tai.adminshop.config.PurchaseLimitManager;
import com.tai.adminshop.economy.Currency;
import com.tai.adminshop.log.TransactionLogger;
import com.tai.adminshop.util.ItemStackSerializer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ShopService {
    public static final int MAX_CUSTOM_BUY_AMOUNT = 2304;

    private ShopService() {
    }

    public static boolean buy(ServerPlayerEntity player, ShopEntry entry, int amount) {
        if (amount <= 0) {
            player.sendMessage(Text.literal("Amount must be greater than 0"), false);
            return false;
        }

        try {
            ItemStack item = ItemStackSerializer.deserializeEntry(entry, player.server.getRegistryManager());
            if (item.isEmpty()) {
                player.sendMessage(Text.literal("Shop item data is invalid: " + entry.id), false);
                return false;
            }

            PurchaseLimitManager.PurchaseLimitStatus limitStatus = AdminShopMod.PURCHASE_LIMIT_MANAGER.status(player.getUuid(), entry);
            if (limitStatus.limited() && (!limitStatus.canBuy() || limitStatus.count() + amount > limitStatus.limit())) {
                player.sendMessage(Text.literal("You have already reached the purchase limit for this item."), false);
                player.sendMessage(Text.literal("Resets in: " + PurchaseLimitManager.formatDuration(limitStatus.resetsIn())), false);
                return false;
            }

            boolean commandReward = entry.isCommandReward();
            double price = multiplyPrice(effectiveBuyPrice(entry), amount);
            if (!commandReward && !canInsertAmount(player.getInventory(), item, amount)) {
                player.sendMessage(Text.literal("Your inventory is full"), false);
                return false;
            }

            String currency = Currency.of(entry);
            if (!Currency.has(player, currency, price)) {
                player.sendMessage(Text.literal("Not enough " + currency), false);
                return false;
            }

            if (!Currency.take(player, currency, price)) {
                player.sendMessage(Text.literal("Could not take " + currency), false);
                return false;
            }

            if (commandReward) {
                if (!runRewardCommand(player, entry, amount)) {
                    Currency.give(player, currency, price);
                    player.sendMessage(Text.literal("Reward command failed. Payment was refunded."), false);
                    return false;
                }
            } else {
                if (!insertAmount(player.getInventory(), item, amount)) {
                    Currency.give(player, currency, price);
                    player.sendMessage(Text.literal("Your inventory is full"), false);
                    return false;
                }
            }

            TransactionLogger.buy(player, entry.id, amount, price);
            if (limitStatus.limited() && !AdminShopMod.PURCHASE_LIMIT_MANAGER.increment(player.getUuid(), entry, amount)) {
                AdminShopMod.LOGGER.warn("AdminShop purchase limit count was not incremented for {} buying {}", player.getUuid(), entry.id);
            }
            player.sendMessage(Text.literal("Bought " + amount + "x " + entry.id + " for " + Currency.format(currency, price)), false);
            return true;
        } catch (ArithmeticException e) {
            player.sendMessage(Text.literal("Price is too large"), false);
            return false;
        } catch (Exception e) {
            AdminShopMod.LOGGER.error("Failed to buy shop item {}", entry.id, e);
            player.sendMessage(Text.literal("Could not buy item: " + entry.id), false);
            return false;
        }
    }

    private static boolean runRewardCommand(ServerPlayerEntity player, ShopEntry entry, int amount) {
        if (entry.rewardCommand == null || entry.rewardCommand.isBlank()) {
            AdminShopMod.LOGGER.error("Command reward shop item {} has an empty rewardCommand", entry.id);
            return false;
        }

        String command = entry.rewardCommand.trim();
        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isBlank() || command.contains("\n") || command.contains("\r")) {
            AdminShopMod.LOGGER.error("Command reward shop item {} has an unsafe rewardCommand", entry.id);
            return false;
        }

        command = command
                .replace("%player%", player.getGameProfile().getName())
                .replace("%uuid%", player.getUuidAsString())
                .replace("%amount%", String.valueOf(amount));

        try {
            MinecraftServer server = player.server;
            ServerCommandSource source = server.getCommandSource();
            ParseResults<ServerCommandSource> parseResults = server.getCommandManager().getDispatcher().parse(command, source);
            int result = server.getCommandManager().getDispatcher().execute(parseResults);
            if (result <= 0) {
                AdminShopMod.LOGGER.error("Command reward shop item {} returned {} for command '{}'", entry.id, result, command);
                return false;
            }
            return true;
        } catch (Exception e) {
            AdminShopMod.LOGGER.error("Command reward shop item {} failed for player {}", entry.id, player.getUuidAsString(), e);
            return false;
        }
    }

    public static boolean sell(ServerPlayerEntity player, ShopEntry entry, int amount) {
        if (effectiveSellPrice(entry) <= 0) {
            player.sendMessage(Text.literal("This item cannot be sold"), false);
            return false;
        }
        if (amount <= 0) {
            player.sendMessage(Text.literal("Amount must be greater than 0"), false);
            return false;
        }

        try {
            ItemStack template = ItemStackSerializer.deserializeEntry(entry, player.server.getRegistryManager());
            if (template.isEmpty()) {
                player.sendMessage(Text.literal("Shop item data is invalid: " + entry.id), false);
                return false;
            }

            int available = countMatching(player.getInventory(), template);
            int sellAmount = Math.min(amount, available);
            if (sellAmount <= 0) {
                player.sendMessage(Text.literal("You do not have that item"), false);
                return false;
            }

            double price = multiplyPrice(effectiveSellPrice(entry), sellAmount);
            if (!removeMatching(player.getInventory(), template, sellAmount)) {
                player.sendMessage(Text.literal("Could not remove item"), false);
                return false;
            }

            String currency = Currency.of(entry);
            if (!Currency.give(player, currency, price)) {
                player.sendMessage(Text.literal("Could not give " + currency), false);
                return false;
            }

            TransactionLogger.sell(player, entry.id, sellAmount, price);
            player.sendMessage(Text.literal("Sold " + sellAmount + "x " + entry.id + " for " + Currency.format(currency, price)), false);
            return true;
        } catch (ArithmeticException e) {
            player.sendMessage(Text.literal("Price is too large"), false);
            return false;
        } catch (Exception e) {
            AdminShopMod.LOGGER.error("Failed to sell shop item {}", entry.id, e);
            player.sendMessage(Text.literal("Could not sell item: " + entry.id), false);
            return false;
        }
    }

    public static Optional<ShopEntry> findSellEntryFor(ItemStack playerItem, RegistryWrapper.WrapperLookup registries) {
        if (playerItem.isEmpty()) {
            return Optional.empty();
        }

        for (ShopEntry entry : AdminShopMod.SHOP_MANAGER.all()) {
            if (effectiveSellPrice(entry) <= 0) {
                continue;
            }
            try {
                ItemStack shopItem = ItemStackSerializer.deserializeEntry(entry, registries);
                if (matches(shopItem, playerItem)) {
                    return Optional.of(entry);
                }
            } catch (Exception e) {
                AdminShopMod.LOGGER.error("Failed to compare shop item {}", entry.id, e);
            }
        }
        return Optional.empty();
    }

    public static boolean matches(ItemStack shopItem, ItemStack playerItem) {
        return !shopItem.isEmpty() && !playerItem.isEmpty() && ItemStack.areItemsAndComponentsEqual(shopItem, playerItem);
    }

    public static boolean sellHand(ServerPlayerEntity player) {
        ItemStack held = player.getInventory().getMainHandStack();
        if (held.isEmpty()) {
            player.sendMessage(Text.literal("Item nay khong ban duoc."), false);
            return false;
        }

        Optional<ShopEntry> entry = findSellEntryFor(held, player.server.getRegistryManager());
        if (entry.isEmpty()) {
            player.sendMessage(Text.literal("Item nay khong ban duoc."), false);
            return false;
        }

        return sell(player, entry.get(), held.getCount());
    }

    public static boolean sellAll(ServerPlayerEntity player) {
        PlayerInventory inventory = player.getInventory();
        RegistryWrapper.WrapperLookup registries = player.server.getRegistryManager();
        Map<String, SellPlan> plans = new LinkedHashMap<>();

        for (int slot = 0; slot < inventory.main.size(); slot++) {
            ItemStack stack = inventory.main.get(slot);
            if (stack.isEmpty()) {
                continue;
            }

            Optional<ShopEntry> entry = findSellEntryFor(stack, registries);
            if (entry.isEmpty()) {
                continue;
            }

            SellPlan plan = plans.computeIfAbsent(entry.get().id, ignored -> new SellPlan(entry.get()));
            plan.add(slot, stack.getCount());
        }

        if (plans.isEmpty()) {
            player.sendMessage(Text.literal("Khong co item nao ban duoc."), false);
            return false;
        }

        Map<String, Double> totalsByCurrency = new LinkedHashMap<>();
        int totalItems = 0;
        for (SellPlan plan : plans.values()) {
            String currency = Currency.of(plan.entry);
            totalsByCurrency.merge(currency, multiplyPrice(effectiveSellPrice(plan.entry), plan.amount), Double::sum);
            totalItems += plan.amount;
        }

        for (SellPlan plan : plans.values()) {
            int remaining = plan.amount;
            for (SlotRemoval removal : plan.removals) {
                ItemStack stack = inventory.main.get(removal.slot);
                int remove = Math.min(remaining, Math.min(removal.amount, stack.getCount()));
                stack.decrement(remove);
                remaining -= remove;
                if (remaining <= 0) {
                    break;
                }
            }
            if (remaining > 0) {
                player.sendMessage(Text.literal("Could not remove all sellable items"), false);
                return false;
            }
        }
        inventory.markDirty();

        for (Map.Entry<String, Double> total : totalsByCurrency.entrySet()) {
            if (!Currency.give(player, total.getKey(), total.getValue())) {
                player.sendMessage(Text.literal("Could not give " + total.getKey()), false);
                return false;
            }
        }

        double totalLogged = totalsByCurrency.values().stream().mapToDouble(Double::doubleValue).sum();
        TransactionLogger.sellAll(player, totalItems, totalLogged);
        player.sendMessage(Text.literal("Da ban:"), false);
        for (SellPlan plan : plans.values()) {
            player.sendMessage(Text.literal("* " + plan.amount + " " + plan.entry.id), false);
        }
        player.sendMessage(Text.literal("Tong nhan:"), false);
        for (Map.Entry<String, Double> total : totalsByCurrency.entrySet()) {
            player.sendMessage(Text.literal(Currency.format(total.getKey(), total.getValue())), false);
        }
        return true;
    }

    public static int maxStackAmount(ShopEntry entry, RegistryWrapper.WrapperLookup registries) {
        try {
            ItemStack item = ItemStackSerializer.deserializeEntry(entry, registries);
            return Math.max(1, item.getMaxCount());
        } catch (Exception e) {
            AdminShopMod.LOGGER.error("Failed to read max stack size for {}", entry.id, e);
            return 1;
        }
    }

    public static double multiplyPrice(double unitPrice, int amount) {
        double total = unitPrice * amount;
        if (!Double.isFinite(total)) {
            throw new ArithmeticException("Price is not finite");
        }
        return total;
    }

    public static double effectiveBuyPrice(ShopEntry entry) {
        return AdminShopMod.PRICE_WINDOW_MANAGER.effectiveBuyPrice(entry);
    }

    public static double effectiveSellPrice(ShopEntry entry) {
        return AdminShopMod.PRICE_WINDOW_MANAGER.effectiveSellPrice(entry);
    }

    private static boolean canInsertAmount(PlayerInventory inventory, ItemStack item, int amount) {
        int remaining = amount;
        int maxCount = Math.max(1, item.getMaxCount());

        for (ItemStack stack : inventory.main) {
            if (stack.isEmpty()) {
                remaining -= maxCount;
            } else if (ItemStack.areItemsAndComponentsEqual(stack, item)) {
                remaining -= Math.max(0, Math.min(stack.getMaxCount(), maxCount) - stack.getCount());
            }

            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean insertAmount(PlayerInventory inventory, ItemStack item, int amount) {
        int remaining = amount;
        int maxCount = Math.max(1, item.getMaxCount());
        while (remaining > 0) {
            int chunk = Math.min(maxCount, remaining);
            ItemStack stack = item.copyWithCount(chunk);
            if (!inventory.insertStack(stack)) {
                return false;
            }
            remaining -= chunk;
        }
        inventory.markDirty();
        return true;
    }

    private static int countMatching(PlayerInventory inventory, ItemStack template) {
        int count = 0;
        for (ItemStack stack : inventory.main) {
            if (matches(template, stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean removeMatching(PlayerInventory inventory, ItemStack template, int amount) {
        if (countMatching(inventory, template) < amount) {
            return false;
        }

        int remaining = amount;
        for (ItemStack stack : inventory.main) {
            if (!matches(template, stack)) {
                continue;
            }
            int remove = Math.min(remaining, stack.getCount());
            stack.decrement(remove);
            remaining -= remove;
            if (remaining <= 0) {
                inventory.markDirty();
                return true;
            }
        }
        return false;
    }

    private static final class SellPlan {
        private final ShopEntry entry;
        private final List<SlotRemoval> removals = new ArrayList<>();
        private int amount;

        private SellPlan(ShopEntry entry) {
            this.entry = entry;
        }

        private void add(int slot, int amount) {
            this.removals.add(new SlotRemoval(slot, amount));
            this.amount += amount;
        }
    }

    private record SlotRemoval(int slot, int amount) {
    }
}
