package com.tai.adminshop.gui;

import com.tai.adminshop.config.StoreEntry;
import com.tai.adminshop.service.StoreService;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class StoreScreenHandler extends GenericContainerScreenHandler {
    private final int size;
    private final List<StoreEntry> entriesBySlot;

    private StoreScreenHandler(ScreenHandlerType<GenericContainerScreenHandler> type, int syncId,
                               PlayerInventory playerInventory, Inventory inventory, int rows,
                               List<StoreEntry> entriesBySlot) {
        super(type, syncId, playerInventory, inventory, rows);
        this.size = rows * 9;
        this.entriesBySlot = entriesBySlot;
    }

    public static StoreScreenHandler create(int syncId, PlayerInventory playerInventory, Inventory inventory,
                                            int rows, List<StoreEntry> entriesBySlot) {
        int safeRows = Math.max(1, Math.min(6, rows));
        return new StoreScreenHandler(typeForRows(safeRows), syncId, playerInventory, inventory, safeRows, entriesBySlot);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || slotIndex < 0 || slotIndex >= size) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }
        StoreEntry entry = slotIndex < entriesBySlot.size() ? entriesBySlot.get(slotIndex) : null;
        if (entry != null) {
            StoreService.buy(serverPlayer, entry);
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        if (player instanceof ServerPlayerEntity serverPlayer && slot >= 0 && slot < entriesBySlot.size()) {
            StoreEntry entry = entriesBySlot.get(slot);
            if (entry != null) {
                StoreService.buy(serverPlayer, entry);
            }
        }
        return ItemStack.EMPTY;
    }

    private static ScreenHandlerType<GenericContainerScreenHandler> typeForRows(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }
}
