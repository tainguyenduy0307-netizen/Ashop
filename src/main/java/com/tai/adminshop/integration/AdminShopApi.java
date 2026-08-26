package com.tai.adminshop.integration;

import com.tai.adminshop.gui.ShopGui;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Small, server-only integration boundary for optional companion mods.
 * Purchase and catalogue authority remain inside AdminShop.
 */
public final class AdminShopApi {
    private AdminShopApi() {
    }

    public static boolean openShop(ServerPlayerEntity player) {
        if (player == null || player.isDisconnected()) {
            return false;
        }
        ShopGui.openMainMenu(player);
        return true;
    }
}
