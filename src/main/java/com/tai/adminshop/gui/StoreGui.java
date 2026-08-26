package com.tai.adminshop.gui;

import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.config.StoreConfig;
import com.tai.adminshop.config.StoreEntry;
import com.tai.adminshop.economy.Currency;
import com.tai.adminshop.service.StoreService;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class StoreGui {
    private StoreGui() {
    }

    public static void open(ServerPlayerEntity player) {
        StoreConfig config = AdminShopMod.STORE_MANAGER.config();
        int rows = Math.max(1, Math.min(6, config.rows));
        int size = rows * 9;
        SimpleInventory inventory = new SimpleInventory(size);
        List<StoreEntry> entriesBySlot = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entriesBySlot.add(null);
            inventory.setStack(i, filler());
        }

        for (StoreEntry entry : config.items) {
            if (entry.slot < 0 || entry.slot >= size) {
                continue;
            }
            ItemStack stack = displayStack(player, entry);
            if (!stack.isEmpty()) {
                inventory.setStack(entry.slot, stack);
                entriesBySlot.set(entry.slot, entry);
            }
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, playerEntity) -> StoreScreenHandler.create(syncId, playerInventory, inventory, rows, entriesBySlot),
                Text.literal(config.title == null ? "Ruby Store" : config.title)
        ));
    }

    private static ItemStack displayStack(ServerPlayerEntity player, StoreEntry entry) {
        ItemStack stack = StoreService.itemFor(player, entry);
        if (stack.isEmpty()) {
            stack = new ItemStack(Items.BARRIER);
        }
        ItemStack display = stack.copyWithCount(1);
        if (entry.name != null && !entry.name.isBlank()) {
            display.set(DataComponentTypes.CUSTOM_NAME, Text.literal(entry.name));
        }

        List<Text> lore = new ArrayList<>();
        if (entry.lore != null) {
            for (String line : entry.lore) {
                lore.add(Text.literal(line));
            }
        }
        lore.add(Text.literal(""));
        lore.add(Text.literal("Price: ").formatted(Formatting.WHITE)
                .append(Text.literal(Currency.format(entry.currency, entry.price)).formatted(Formatting.GREEN)));
        if (Currency.SAPPHIRE.equals(Currency.normalize(entry.currency))) {
            lore.add(Text.literal("Ruby may cover missing Sapphire").formatted(Formatting.GRAY));
        }
        if (entry.requiredGroup != null && !entry.requiredGroup.isBlank()) {
            lore.add(Text.literal("Requires: " + entry.requiredGroup).formatted(Formatting.YELLOW));
        }
        lore.add(Text.literal("Click to buy").formatted(Formatting.AQUA));
        display.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return display;
    }

    private static ItemStack filler() {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        return stack;
    }
}
