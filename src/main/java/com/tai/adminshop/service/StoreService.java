package com.tai.adminshop.service;

import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.config.StoreEntry;
import com.tai.adminshop.economy.Currency;
import com.tai.adminshop.economy.PaymentPolicy;
import com.tai.adminshop.util.ItemStackSerializer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.track.PromotionResult;
import net.luckperms.api.track.Track;

import java.util.Locale;

public final class StoreService {
    private static final String DEFAULT_TRACK = "ranks";

    private StoreService() {
    }

    public static boolean buy(ServerPlayerEntity player, StoreEntry entry) {
        RankPurchase rankPurchase = null;
        if (entry.requiredGroup != null && !entry.requiredGroup.isBlank()) {
            rankPurchase = validateRankPurchase(player, entry.requiredGroup, trackName(entry));
            if (!rankPurchase.valid()) {
                player.sendMessage(Text.literal(rankPurchase.message()), false);
                return false;
            }
        }
        PaymentPolicy.Result payment = PaymentPolicy.charge(player, entry.currency, entry.price);
        if (!payment.successful()) {
            player.sendMessage(Text.literal(payment.message()), false);
            return false;
        }
        if (entry.giveItem) {
            ItemStack stack = itemFor(player, entry);
            if (stack.isEmpty()) {
                PaymentPolicy.refund(player, payment.receipt());
                player.sendMessage(Text.literal("Store item data is invalid: " + entry.id), false);
                return false;
            }
            if (!player.getInventory().insertStack(stack.copy())) {
                PaymentPolicy.refund(player, payment.receipt());
                player.sendMessage(Text.literal("Your inventory is full."), false);
                return false;
            }
        }

        if (rankPurchase != null && !promoteRank(player, entry, rankPurchase, payment.receipt())) {
            return false;
        }
        if (rankPurchase == null) {
            runCommands(player, entry);
        }
        player.sendMessage(Text.literal("Purchased " + displayName(entry) + " for "
                + Currency.format(entry.currency, entry.price) + "."), false);
        return true;
    }

    public static ItemStack itemFor(ServerPlayerEntity player, StoreEntry entry) {
        try {
            if (entry.itemData != null && !entry.itemData.isBlank()) {
                return ItemStackSerializer.deserialize(entry.itemData, player.server.getRegistryManager());
            }
            return ItemStackSerializer.fromItemId(entry.displayItem);
        } catch (Exception e) {
            AdminShopMod.LOGGER.error("Failed to load store item {}", entry.id, e);
            return ItemStack.EMPTY;
        }
    }

    private static void runCommands(ServerPlayerEntity player, StoreEntry entry) {
        MinecraftServer server = player.server;
        for (String configured : entry.commands) {
            if (configured == null || configured.isBlank()) {
                continue;
            }
            String command = configured
                    .replace("%player%", player.getGameProfile().getName())
                    .replace("%uuid%", player.getUuidAsString());
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
        }
    }

    private static RankPurchase validateRankPurchase(ServerPlayerEntity player, String requiredGroup, String trackName) {
        String normalizedRequiredGroup = normalizeGroup(requiredGroup);
        if (normalizedRequiredGroup.isBlank()) {
            return RankPurchase.invalid("Store group requirement is invalid.");
        }
        String normalizedTrackName = normalizeGroup(trackName);
        if (normalizedTrackName.isBlank()) {
            return RankPurchase.invalid("Store track is invalid.");
        }
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();
            User user = luckPerms.getUserManager().getUser(player.getUuid());
            if (user == null) {
                user = luckPerms.getUserManager().loadUser(player.getUuid(), player.getGameProfile().getName()).join();
            }
            if (user == null) {
                return RankPurchase.invalid("Could not load your LuckPerms user data.");
            }

            Track track = luckPerms.getTrackManager().getTrack(normalizedTrackName);
            if (track == null) {
                return RankPurchase.invalid("LuckPerms track is not loaded: " + normalizedTrackName);
            }

            if (!hasRequiredGroup(user, requiredGroup)) {
                return RankPurchase.invalid("You do not meet the rank requirement for this purchase.");
            }
            if (!track.containsGroup(normalizedRequiredGroup)) {
                return RankPurchase.invalid("LuckPerms track " + normalizedTrackName
                        + " does not contain group " + normalizedRequiredGroup + ".");
            }

            Group group = luckPerms.getGroupManager().getGroup(normalizedRequiredGroup);
            if (group == null) {
                return RankPurchase.invalid("LuckPerms group is not loaded: " + normalizedRequiredGroup);
            }
            String nextGroup = normalizeGroup(track.getNext(group));
            if (nextGroup.isBlank()) {
                return RankPurchase.invalid("You do not have another group to buy.");
            }
            return new RankPurchase(true, "", luckPerms, user, track, normalizedRequiredGroup, nextGroup);
        } catch (IllegalStateException | LinkageError e) {
            AdminShopMod.LOGGER.warn("LuckPerms store check failed for group {} on track {}", requiredGroup, trackName, e);
            return RankPurchase.invalid("LuckPerms is not available.");
        } catch (RuntimeException e) {
            AdminShopMod.LOGGER.warn("LuckPerms store check failed for group {} on track {}", requiredGroup, trackName, e);
            return RankPurchase.invalid("Could not verify your rank.");
        }
    }

    private static boolean promoteRank(ServerPlayerEntity player, StoreEntry entry, RankPurchase rankPurchase, com.tai.adminshop.economy.PaymentReceipt receipt) {
        try {
            PromotionResult result = rankPurchase.track().promote(rankPurchase.user(), ImmutableContextSet.empty());
            if (!result.wasSuccessful()) {
                PaymentPolicy.refund(player, receipt);
                player.sendMessage(Text.literal("Could not promote your rank. Payment was refunded."), false);
                return false;
            }
            String promotedTo = normalizeGroup(result.getGroupTo().orElse(""));
            if (!rankPurchase.nextGroup().equals(promotedTo)) {
                PaymentPolicy.refund(player, receipt);
                player.sendMessage(Text.literal("Promotion did not match this store item. Payment was refunded."), false);
                return false;
            }
            rankPurchase.luckPerms().getUserManager().saveUser(rankPurchase.user()).join();
            return true;
        } catch (RuntimeException | LinkageError e) {
            PaymentPolicy.refund(player, receipt);
            AdminShopMod.LOGGER.warn("LuckPerms rank promotion failed for {}", player.getGameProfile().getName(), e);
            player.sendMessage(Text.literal("Could not promote your rank. Payment was refunded."), false);
            return false;
        }
    }

    private static String trackName(StoreEntry entry) {
        return entry.track == null || entry.track.isBlank() ? DEFAULT_TRACK : entry.track;
    }

    private static boolean hasRequiredGroup(User user, String requiredGroup) {
        if (requiredGroup == null || requiredGroup.isBlank()) {
            return true;
        }
        String primaryGroup = user.getPrimaryGroup();
        if (primaryGroup != null && primaryGroup.equalsIgnoreCase(requiredGroup)) {
            return true;
        }
        for (InheritanceNode node : user.getNodes(NodeType.INHERITANCE)) {
            String groupName = node.getGroupName();
            if (groupName != null && groupName.equalsIgnoreCase(requiredGroup)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeGroup(String group) {
        return group == null ? "" : group.trim().toLowerCase(Locale.ROOT);
    }

    private record RankPurchase(boolean valid, String message, LuckPerms luckPerms, User user, Track track,
                                String requiredGroup, String nextGroup) {
        private static RankPurchase invalid(String message) {
            return new RankPurchase(false, message, null, null, null, "", "");
        }
    }

    private static String displayName(StoreEntry entry) {
        return entry.name == null || entry.name.isBlank() ? entry.id : entry.name;
    }
}
