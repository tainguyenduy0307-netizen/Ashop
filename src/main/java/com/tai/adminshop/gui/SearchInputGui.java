package com.tai.adminshop.gui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public final class SearchInputGui {
    private SearchInputGui() {
    }

    public static void open(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, playerEntity) -> new SearchInputScreenHandler(syncId, playerInventory, player),
                Text.literal("Search Item")
        ));
    }

    private static final class SearchInputScreenHandler extends AnvilScreenHandler {
        private final ServerPlayerEntity player;
        private String inputText = "";

        private SearchInputScreenHandler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity player) {
            super(syncId, playerInventory, ScreenHandlerContext.EMPTY);
            this.player = player;

            ItemStack input = new ItemStack(Items.PAPER);
            input.set(DataComponentTypes.CUSTOM_NAME, Text.literal(""));
            this.input.setStack(INPUT_1_ID, input);
            updateResult();
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }

        @Override
        public boolean setNewItemName(String newItemName) {
            this.inputText = newItemName == null ? "" : newItemName.trim();
            updateResult();
            return true;
        }

        @Override
        public void updateResult() {
            this.output.setStack(0, createOutputStack());
            sendContentUpdates();
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
            if (!(playerEntity instanceof ServerPlayerEntity serverPlayer)) {
                return;
            }

            if (slotIndex == OUTPUT_ID) {
                ShopGui.openSearch(serverPlayer, inputText, 0);
            }
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void onClosed(PlayerEntity player) {
            this.input.clear();
            this.output.clear();
            super.onClosed(player);
        }

        @Override
        protected boolean canTakeOutput(PlayerEntity player, boolean present) {
            return false;
        }

        @Override
        protected void onTakeOutput(PlayerEntity player, ItemStack stack) {
        }

        @Override
        public int getLevelCost() {
            return 0;
        }

        private ItemStack createOutputStack() {
            ItemStack output = new ItemStack(Items.COMPASS);
            output.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Search"));
            output.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("Keyword: " + inputText),
                    Text.literal("Click de tim")
            )));
            return output;
        }
    }
}
