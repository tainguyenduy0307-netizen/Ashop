package com.tai.adminshop.gui;

import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.config.PriceWindowManager;
import com.tai.adminshop.config.PurchaseLimitManager;
import com.tai.adminshop.config.ShopCategory;
import com.tai.adminshop.config.ShopEntry;
import com.tai.adminshop.economy.Currency;
import com.tai.adminshop.service.ShopService;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class AdminShopScreenHandler extends GenericContainerScreenHandler {
    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;
    public static final int ITEMS_PER_PAGE = 45;

    public static final int SLOT_BACK = 45;
    public static final int SLOT_PREVIOUS = 48;
    public static final int SLOT_PAGE = 49;
    public static final int SLOT_NEXT = 50;
    public static final int SLOT_SEARCH = 51;
    public static final int SLOT_CLOSE = 53;
    public static final int SLOT_MAIN_SEARCH = 49;

    private static final int[] MAIN_CATEGORY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final boolean mainMenu;
    private final boolean searchResults;
    private final List<ShopCategory> categories;
    private final List<ShopEntry> entries;
    private final String category;
    private final String keyword;
    private final int page;
    private final int totalPages;
    private final int backSlot;
    private final int previousSlot;
    private final int nextSlot;
    private final RegistryWrapper.WrapperLookup registries;

    private AdminShopScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory,
                                   boolean mainMenu, boolean searchResults, List<ShopCategory> categories,
                                   List<ShopEntry> entries, String category, String keyword, int page, int totalPages,
                                   int backSlot, int previousSlot, int nextSlot, RegistryWrapper.WrapperLookup registries) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, ROWS);
        this.mainMenu = mainMenu;
        this.searchResults = searchResults;
        this.categories = categories;
        this.entries = entries;
        this.category = category;
        this.keyword = keyword;
        this.page = page;
        this.totalPages = totalPages;
        this.backSlot = backSlot;
        this.previousSlot = previousSlot;
        this.nextSlot = nextSlot;
        this.registries = registries;
    }

    public static AdminShopScreenHandler mainMenu(int syncId, PlayerInventory playerInventory, Inventory inventory,
                                                  List<ShopCategory> categories) {
        return new AdminShopScreenHandler(syncId, playerInventory, inventory, true, false, categories, List.of(), "", "", 0, 1, SLOT_BACK, SLOT_PREVIOUS, SLOT_NEXT, null);
    }

    public static AdminShopScreenHandler category(int syncId, PlayerInventory playerInventory, Inventory inventory,
                                                  List<ShopEntry> entries, String category, int page, int totalPages,
                                                  RegistryWrapper.WrapperLookup registries) {
        return category(syncId, playerInventory, inventory, entries, category, page, totalPages, SLOT_BACK, SLOT_PREVIOUS, SLOT_NEXT, registries);
    }

    public static AdminShopScreenHandler category(int syncId, PlayerInventory playerInventory, Inventory inventory,
                                                  List<ShopEntry> entries, String category, int page, int totalPages,
                                                  int backSlot, int previousSlot, int nextSlot,
                                                  RegistryWrapper.WrapperLookup registries) {
        return new AdminShopScreenHandler(syncId, playerInventory, inventory, false, false, List.of(), entries, category, "", page, totalPages, backSlot, previousSlot, nextSlot, registries);
    }

    public static AdminShopScreenHandler search(int syncId, PlayerInventory playerInventory, Inventory inventory,
                                                List<ShopEntry> entries, String keyword, int page, int totalPages,
                                                RegistryWrapper.WrapperLookup registries) {
        return new AdminShopScreenHandler(syncId, playerInventory, inventory, false, true, List.of(), entries, "", keyword, page, totalPages, SLOT_BACK, SLOT_PREVIOUS, SLOT_NEXT, registries);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer) || slotIndex < 0 || slotIndex >= SIZE) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }

        if (mainMenu) {
            handleMainMenuClick(serverPlayer, slotIndex);
            return;
        }

        if (slotIndex < entries.size() && entries.get(slotIndex) != null) {
            handleShopItemClick(serverPlayer, slotIndex, button, actionType);
            return;
        }

        handleCategoryControlClick(serverPlayer, slotIndex);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        if (!mainMenu && player instanceof ServerPlayerEntity serverPlayer && slot >= 0 && slot < entries.size() && entries.get(slot) != null) {
            handleShopItemClick(serverPlayer, slot, 0, SlotActionType.QUICK_MOVE);
        }
        return ItemStack.EMPTY;
    }

    private void handleMainMenuClick(ServerPlayerEntity player, int slotIndex) {
        if (slotIndex == SLOT_MAIN_SEARCH) {
            SearchInputGui.open(player);
            return;
        }

        for (int i = 0; i < MAIN_CATEGORY_SLOTS.length && i < categories.size(); i++) {
            if (slotIndex == MAIN_CATEGORY_SLOTS[i]) {
                ShopGui.openCategory(player, categories.get(i).id(), 0);
                return;
            }
        }
    }

    private void handleShopItemClick(ServerPlayerEntity player, int slotIndex, int button, SlotActionType actionType) {
        if (slotIndex >= entries.size() || entries.get(slotIndex) == null) {
            return;
        }

        ShopEntry entry = entries.get(slotIndex);
        int quantity = Math.max(1, entry.quantity);
        int stackAmount = ShopService.maxStackAmount(entry, registries);

        if (actionType == SlotActionType.CLONE || actionType == SlotActionType.THROW || button == 2) {
            AmountInputGui.open(player, entry);
            return;
        }

        if (actionType == SlotActionType.QUICK_MOVE) {
            if (button == 1) {
                ShopService.sell(player, entry, stackAmount);
            } else {
                ShopService.buy(player, entry, stackAmount);
            }
            return;
        }

        if (button == 1) {
            ShopService.sell(player, entry, quantity);
        } else {
            ShopService.buy(player, entry, quantity);
        }
    }

    private void handleCategoryControlClick(ServerPlayerEntity player, int slotIndex) {
        if (slotIndex == backSlot) {
            ShopGui.openMainMenu(player);
        } else if (slotIndex == previousSlot && page > 0) {
            openCurrentPage(player, page - 1);
        } else if (slotIndex == nextSlot && page + 1 < totalPages) {
            openCurrentPage(player, page + 1);
        } else if (slotIndex == SLOT_SEARCH) {
            SearchInputGui.open(player);
        } else if (slotIndex == SLOT_CLOSE) {
            player.closeHandledScreen();
        }
    }

    private void openCurrentPage(ServerPlayerEntity player, int targetPage) {
        if (searchResults) {
            ShopGui.openSearch(player, keyword, targetPage);
        } else {
            ShopGui.openCategory(player, category, targetPage);
        }
    }

    public static ItemStack displayStack(ItemStack stack, ShopEntry entry, ServerPlayerEntity player) {
        ItemStack display = stack.copyWithCount(1);
        PriceWindowManager.PriceWindowInfo priceInfo = AdminShopMod.PRICE_WINDOW_MANAGER.info(entry);
        PurchaseLimitManager.PurchaseLimitStatus limitStatus = AdminShopMod.PURCHASE_LIMIT_MANAGER.status(player.getUuid(), entry);
        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("ID: " + entry.id));
        lore.add(Text.literal("Category: " + ShopEntry.normalizeCategory(entry.category)));
        lore.add(Text.literal("Left Click: Buy 1"));
        lore.add(Text.literal("Shift Left: Buy Stack"));
        lore.add(Text.literal("Middle Click / Q: Buy Custom Amount"));
        if (priceInfo.currentSell() > 0) {
            lore.add(Text.literal("Right Click: Sell 1"));
            lore.add(Text.literal("Shift Right: Sell Stack"));
        }
        String currency = Currency.of(entry);
        lore.add(priceLine("Buy:  ", currency, priceInfo.currentBuy(), Formatting.GREEN));
        if (priceInfo.currentSell() > 0) {
            lore.add(priceLine("Sell: ", currency, priceInfo.currentSell(), Formatting.RED));
        }
        if (entry.isCommandReward()) {
            lore.add(Text.literal("Reward: Command Bundle").formatted(Formatting.AQUA));
        }
        if (priceInfo.dynamic()) {
            if (priceInfo.multiplier() > 1.0D) {
                lore.add(Text.literal("▲ Market Up").formatted(Formatting.GREEN));
            } else if (priceInfo.multiplier() < 1.0D) {
                lore.add(Text.literal("▼ Market Down").formatted(Formatting.RED));
            }
        }
        if (limitStatus.limited()) {
            Formatting countColor = limitStatus.canBuy() ? Formatting.GREEN : Formatting.RED;
            lore.add(Text.literal(""));
            lore.add(Text.literal("Limit: ").formatted(Formatting.WHITE)
                    .append(Text.literal(PurchaseLimitManager.label(entry)).formatted(Formatting.GREEN)));
            lore.add(Text.literal("Bought: ").formatted(Formatting.WHITE)
                    .append(Text.literal(limitStatus.count() + "/" + limitStatus.limit()).formatted(countColor)));
            if (limitStatus.canBuy()) {
                lore.add(Text.literal("Reset sau: ").formatted(Formatting.WHITE)
                        .append(Text.literal(PurchaseLimitManager.formatDuration(limitStatus.resetsIn())).formatted(Formatting.YELLOW)));
            } else {
                lore.add(Text.literal("Trạng thái: ").formatted(Formatting.WHITE)
                        .append(Text.literal("Đã đạt giới hạn").formatted(Formatting.RED)));
                lore.add(Text.literal("Reset sau: " + PurchaseLimitManager.formatDuration(limitStatus.resetsIn())).formatted(Formatting.YELLOW));
            }
        }
        display.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return display;
    }

    private static Text priceLine(String label, String currency, double price, Formatting valueColor) {
        return Text.literal(label).formatted(Formatting.WHITE)
                .append(Text.literal(Currency.format(currency, price)).formatted(valueColor));
    }
}

