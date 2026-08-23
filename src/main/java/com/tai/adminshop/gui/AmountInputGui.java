package com.tai.adminshop.gui;

import com.tai.adminshop.config.ShopEntry;
import com.tai.adminshop.economy.Currency;
import com.tai.adminshop.service.ShopService;
import com.tai.adminshop.util.ItemStackSerializer;
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
import net.minecraft.util.Formatting;

import java.util.List;

public final class AmountInputGui {
    private AmountInputGui() {
    }

    public static void open(ServerPlayerEntity player, ShopEntry entry) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, playerEntity) -> new AmountInputScreenHandler(syncId, playerInventory, player, entry),
                Text.literal("Nháº­p sá»‘ lÆ°á»£ng")
        ));
    }

    private static final class AmountInputScreenHandler extends AnvilScreenHandler {
        private final ServerPlayerEntity player;
        private final ShopEntry entry;
        private final String itemName;
        private String inputText = "1";

        private AmountInputScreenHandler(int syncId, PlayerInventory playerInventory, ServerPlayerEntity player, ShopEntry entry) {
            super(syncId, playerInventory, ScreenHandlerContext.EMPTY);
            this.player = player;
            this.entry = entry;
            this.itemName = getShopItemName(player, entry);

            ItemStack input = new ItemStack(Items.PAPER);
            input.set(DataComponentTypes.CUSTOM_NAME, Text.literal("1"));
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
                confirm(serverPlayer);
                return;
            }

            // This GUI is only for text input; blocking slot moves prevents dupes and paper leakage.
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

        private void confirm(ServerPlayerEntity serverPlayer) {
            Integer amount = parseAmount();
            if (amount == null) {
                serverPlayer.sendMessage(Text.literal("Sá»‘ lÆ°á»£ng khÃ´ng há»£p lá»‡."), false);
                updateResult();
                return;
            }

            boolean bought = ShopService.buy(serverPlayer, entry, amount);
            if (bought) {
                serverPlayer.closeHandledScreen();
            }
        }

        private ItemStack createOutputStack() {
            Integer amount = parseAmount();
            if (amount == null) {
                ItemStack invalid = new ItemStack(Items.BARRIER);
                invalid.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Sá»‘ lÆ°á»£ng khÃ´ng há»£p lá»‡"));
                return invalid;
            }

            ItemStack output = new ItemStack(Items.PAPER);
            output.set(DataComponentTypes.CUSTOM_NAME, Text.literal("XÃ¡c nháº­n mua"));
            String totalPrice;
            try {
                totalPrice = Currency.format(entry.currency, ShopService.multiplyPrice(ShopService.effectiveBuyPrice(entry), amount));
            } catch (ArithmeticException e) {
                totalPrice = "quÃ¡ lá»›n";
            }
            output.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                    Text.literal("Item: " + itemName),
                    Text.literal("Sá»‘ lÆ°á»£ng: " + amount),
                    Text.literal("Total: ").formatted(Formatting.WHITE)
                            .append(Text.literal(totalPrice).formatted(Formatting.GREEN)),
                    Text.literal("Click Ä‘á»ƒ mua")
            )));
            return output;
        }

        private Integer parseAmount() {
            int amount;
            try {
                amount = Integer.parseInt(inputText);
            } catch (NumberFormatException e) {
                return null;
            }

            if (amount <= 0 || amount > ShopService.MAX_CUSTOM_BUY_AMOUNT) {
                return null;
            }
            try {
                ShopService.multiplyPrice(ShopService.effectiveBuyPrice(entry), amount);
            } catch (ArithmeticException e) {
                return null;
            }
            return amount;
        }

        private static String getShopItemName(ServerPlayerEntity player, ShopEntry entry) {
            try {
                ItemStack stack = ItemStackSerializer.deserializeEntry(entry, player.server.getRegistryManager());
                if (!stack.isEmpty()) {
                    return stack.getName().getString();
                }
            } catch (Exception ignored) {
            }
            return entry.id;
        }
    }
}

