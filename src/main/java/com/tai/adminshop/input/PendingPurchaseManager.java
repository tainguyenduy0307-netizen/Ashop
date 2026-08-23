package com.tai.adminshop.input;

import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.config.ShopEntry;
import com.tai.adminshop.service.ShopService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingPurchaseManager {
    private static final long TIMEOUT_MILLIS = 30_000L;
    private static final Map<UUID, PendingPurchase> PENDING = new ConcurrentHashMap<>();

    private PendingPurchaseManager() {
    }

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(PendingPurchaseManager::onChat);
        ServerTickEvents.END_SERVER_TICK.register(PendingPurchaseManager::tick);
    }

    public static void begin(ServerPlayerEntity player, ShopEntry entry) {
        PENDING.put(player.getUuid(), new PendingPurchase(entry, System.currentTimeMillis() + TIMEOUT_MILLIS));
        player.closeHandledScreen();
        player.sendMessage(Text.literal("Nhap so luong muon mua hoac go cancel de huy."), false);
    }

    private static boolean onChat(SignedMessage message, ServerPlayerEntity player, net.minecraft.network.message.MessageType.Parameters parameters) {
        PendingPurchase pending = PENDING.remove(player.getUuid());
        if (pending == null) {
            return true;
        }

        String input = message.getSignedContent().trim();
        if (System.currentTimeMillis() > pending.expiresAt) {
            player.sendMessage(Text.literal("Purchase input timed out."), false);
            return false;
        }

        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(Text.literal("Purchase cancelled."), false);
            return false;
        }

        int amount;
        try {
            amount = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            player.sendMessage(Text.literal("Invalid amount."), false);
            return false;
        }

        if (amount <= 0) {
            player.sendMessage(Text.literal("Amount must be greater than 0."), false);
            return false;
        }
        if (amount > ShopService.MAX_CUSTOM_BUY_AMOUNT) {
            player.sendMessage(Text.literal("Amount must be <= " + ShopService.MAX_CUSTOM_BUY_AMOUNT + "."), false);
            return false;
        }

        ShopService.buy(player, pending.entry, amount);
        return false;
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, PendingPurchase>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingPurchase> mapEntry = iterator.next();
            if (now <= mapEntry.getValue().expiresAt) {
                continue;
            }

            iterator.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(mapEntry.getKey());
            if (player != null) {
                player.sendMessage(Text.literal("Purchase input timed out."), false);
            }
        }
    }

    private record PendingPurchase(ShopEntry entry, long expiresAt) {
    }
}
