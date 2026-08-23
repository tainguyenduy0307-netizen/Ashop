package com.tai.adminshop.gui;

import com.tai.adminshop.AdminShopMod;
import com.tai.adminshop.config.CategoryShopConfig;
import com.tai.adminshop.config.ShopCategory;
import com.tai.adminshop.config.ShopEntry;
import com.tai.adminshop.util.ItemStackSerializer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ShopGui {
    private static final int[] CATEGORY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private ShopGui() {
    }

    public static void openMainMenu(ServerPlayerEntity player) {
        SimpleInventory inventory = new SimpleInventory(AdminShopScreenHandler.SIZE);
        fillBorder(inventory, Items.GRAY_STAINED_GLASS_PANE);

        List<ShopCategory> categories = ShopCategory.getAllCategories();
        for (int i = 0; i < categories.size() && i < CATEGORY_SLOTS.length; i++) {
            ShopCategory category = categories.get(i);
            inventory.setStack(CATEGORY_SLOTS[i], categoryIcon(category));
        }
        inventory.setStack(AdminShopScreenHandler.SLOT_MAIN_SEARCH, searchIcon());

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, playerEntity) -> AdminShopScreenHandler.mainMenu(syncId, playerInventory, inventory, categories),
                Text.literal("AdminShop")
        ));
    }

    public static void openCategory(ServerPlayerEntity player, String category, int page) {
        var categoryConfig = AdminShopMod.SHOP_MANAGER.categoryConfig(category);
        if (categoryConfig.isPresent()) {
            openConfiguredCategory(player, categoryConfig.get(), page);
            return;
        }

        String normalizedCategory = ShopEntry.normalizeCategory(category);
        String displayName = ShopCategory.displayName(normalizedCategory);
        List<ShopEntry> filtered = AdminShopMod.SHOP_MANAGER.all().stream()
                .filter(entry -> normalizedCategory.equals(ShopEntry.normalizeCategory(entry.category)))
                .sorted(Comparator.comparing(entry -> entry.id == null ? "" : entry.id))
                .toList();

        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) AdminShopScreenHandler.ITEMS_PER_PAGE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int fromIndex = safePage * AdminShopScreenHandler.ITEMS_PER_PAGE;
        int toIndex = Math.min(filtered.size(), fromIndex + AdminShopScreenHandler.ITEMS_PER_PAGE);
        List<ShopEntry> pageEntries = new ArrayList<>(filtered.subList(fromIndex, toIndex));

        SimpleInventory inventory = new SimpleInventory(AdminShopScreenHandler.SIZE);
        fillItems(player, inventory, pageEntries, filtered.isEmpty(), "Danh muc nay chua co item.");
        fillCategoryControls(inventory, safePage, totalPages);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, playerEntity) -> AdminShopScreenHandler.category(
                        syncId,
                        playerInventory,
                        inventory,
                        pageEntries,
                        normalizedCategory,
                        safePage,
                        totalPages,
                        player.server.getRegistryManager()),
                Text.literal("AdminShop - " + displayName + " Page " + (safePage + 1) + " / " + totalPages)
        ));
    }

    private static void openConfiguredCategory(ServerPlayerEntity player, CategoryShopConfig config, int page) {
        int totalPages = Math.max(1, config.items.stream().mapToInt(entry -> Math.max(1, entry.page)).max().orElse(1));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int displayPage = safePage + 1;

        SimpleInventory inventory = new SimpleInventory(AdminShopScreenHandler.SIZE);
        fillAll(inventory, itemFromId(config.fillItem, Items.GRAY_STAINED_GLASS_PANE));
        List<ShopEntry> entriesBySlot = new ArrayList<>();
        for (int i = 0; i < AdminShopScreenHandler.SIZE; i++) {
            entriesBySlot.add(null);
        }

        for (ShopEntry entry : config.items) {
            if (entry.page != displayPage || entry.slot < 0 || entry.slot >= AdminShopScreenHandler.SIZE) {
                continue;
            }
            try {
                ItemStack stack = ItemStackSerializer.deserializeEntry(entry, player.server.getRegistryManager());
                if (!stack.isEmpty()) {
                    inventory.setStack(entry.slot, AdminShopScreenHandler.displayStack(stack.copyWithCount(Math.max(1, entry.quantity)), entry, player));
                    entriesBySlot.set(entry.slot, entry);
                }
            } catch (Exception e) {
                AdminShopMod.LOGGER.error("Failed to load configured shop item {}", entry.id, e);
            }
        }

        fillConfiguredControls(inventory, config, safePage, totalPages);
        clearControlEntry(entriesBySlot, config.buttons.back);
        clearControlEntry(entriesBySlot, config.buttons.previousPage);
        clearControlEntry(entriesBySlot, config.buttons.nextPage);
        clearControlEntry(entriesBySlot, AdminShopScreenHandler.SLOT_SEARCH);
        String title = (config.title == null ? config.displayName : config.title)
                .replace("%page%", String.valueOf(displayPage))
                .replace("%maxPage%", String.valueOf(totalPages));

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, playerEntity) -> AdminShopScreenHandler.category(
                        syncId,
                        playerInventory,
                        inventory,
                        entriesBySlot,
                        config.id,
                        safePage,
                        totalPages,
                        config.buttons.back,
                        config.buttons.previousPage,
                        config.buttons.nextPage,
                        player.server.getRegistryManager()),
                Text.literal(title)
        ));
    }

    public static void openSearch(ServerPlayerEntity player, String keyword, int page) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<ShopEntry> filtered = AdminShopMod.SHOP_MANAGER.all().stream()
                .filter(entry -> matchesSearch(player, entry, normalizedKeyword))
                .sorted(Comparator.comparing(entry -> entry.id == null ? "" : entry.id))
                .toList();

        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) AdminShopScreenHandler.ITEMS_PER_PAGE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int fromIndex = safePage * AdminShopScreenHandler.ITEMS_PER_PAGE;
        int toIndex = Math.min(filtered.size(), fromIndex + AdminShopScreenHandler.ITEMS_PER_PAGE);
        List<ShopEntry> pageEntries = new ArrayList<>(filtered.subList(fromIndex, toIndex));

        SimpleInventory inventory = new SimpleInventory(AdminShopScreenHandler.SIZE);
        fillItems(player, inventory, pageEntries, filtered.isEmpty(), "Không tìm thấy item");
        fillSearchControls(inventory, safePage, totalPages);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInventory, playerEntity) -> AdminShopScreenHandler.search(
                        syncId,
                        playerInventory,
                        inventory,
                        pageEntries,
                        normalizedKeyword,
                        safePage,
                        totalPages,
                        player.server.getRegistryManager()),
                Text.literal("Search: " + normalizedKeyword)
        ));
    }

    private static void fillItems(ServerPlayerEntity player, SimpleInventory inventory, List<ShopEntry> entries, boolean empty, String emptyMessage) {
        if (empty) {
            ItemStack barrier = new ItemStack(Items.BARRIER);
            barrier.set(DataComponentTypes.CUSTOM_NAME, Text.literal(emptyMessage));
            inventory.setStack(22, barrier);
            return;
        }

        for (int i = 0; i < entries.size(); i++) {
            ShopEntry entry = entries.get(i);
            try {
                ItemStack stack = ItemStackSerializer.deserializeEntry(entry, player.server.getRegistryManager());
                if (!stack.isEmpty()) {
                    inventory.setStack(i, AdminShopScreenHandler.displayStack(stack, entry, player));
                }
            } catch (Exception e) {
                AdminShopMod.LOGGER.error("Failed to load shop GUI item {}", entry.id, e);
            }
        }
    }

    private static void fillCategoryControls(SimpleInventory inventory, int page, int totalPages) {
        for (int slot = AdminShopScreenHandler.ITEMS_PER_PAGE; slot < AdminShopScreenHandler.SIZE; slot++) {
            inventory.setStack(slot, filler(Items.GRAY_STAINED_GLASS_PANE));
        }
        inventory.setStack(AdminShopScreenHandler.SLOT_BACK, control(Items.ARROW, "Back to Main Menu", List.of(Text.literal("Click de quay lai menu chinh"))));
        inventory.setStack(AdminShopScreenHandler.SLOT_PREVIOUS, control(Items.ARROW, "Previous Page", List.of(Text.literal("Trang truoc"))));
        inventory.setStack(AdminShopScreenHandler.SLOT_PAGE, pageInfo(page, totalPages));
        inventory.setStack(AdminShopScreenHandler.SLOT_NEXT, control(Items.ARROW, "Next Page", List.of(Text.literal("Trang tiep"))));
        inventory.setStack(AdminShopScreenHandler.SLOT_SEARCH, searchIcon());
        inventory.setStack(AdminShopScreenHandler.SLOT_CLOSE, control(Items.BARRIER, "Close", List.of(Text.literal("Dong shop"))));
    }

    private static void fillConfiguredControls(SimpleInventory inventory, CategoryShopConfig config, int page, int totalPages) {
        if (config.buttons.back >= 0 && config.buttons.back < AdminShopScreenHandler.SIZE) {
            inventory.setStack(config.buttons.back, control(Items.ARROW, "Back to Main Menu", List.of(Text.literal("Click de quay lai menu chinh"))));
        }
        if (config.buttons.previousPage >= 0 && config.buttons.previousPage < AdminShopScreenHandler.SIZE) {
            inventory.setStack(config.buttons.previousPage, control(Items.ARROW, "Previous Page", List.of(Text.literal("Trang truoc"))));
        }
        if (config.buttons.nextPage >= 0 && config.buttons.nextPage < AdminShopScreenHandler.SIZE) {
            inventory.setStack(config.buttons.nextPage, control(Items.ARROW, "Next Page", List.of(Text.literal("Trang tiep"))));
        }
        inventory.setStack(AdminShopScreenHandler.SLOT_SEARCH, searchIcon());
    }

    private static void clearControlEntry(List<ShopEntry> entriesBySlot, int slot) {
        if (slot >= 0 && slot < entriesBySlot.size()) {
            entriesBySlot.set(slot, null);
        }
    }

    private static void fillSearchControls(SimpleInventory inventory, int page, int totalPages) {
        for (int slot = AdminShopScreenHandler.ITEMS_PER_PAGE; slot < AdminShopScreenHandler.SIZE; slot++) {
            inventory.setStack(slot, filler(Items.GRAY_STAINED_GLASS_PANE));
        }
        inventory.setStack(AdminShopScreenHandler.SLOT_BACK, control(Items.ARROW, "Back to Main Menu", List.of(Text.literal("Click de quay lai menu chinh"))));
        inventory.setStack(AdminShopScreenHandler.SLOT_PREVIOUS, control(Items.ARROW, "Previous Page", List.of(Text.literal("Trang truoc"))));
        inventory.setStack(AdminShopScreenHandler.SLOT_PAGE, pageInfo(page, totalPages));
        inventory.setStack(AdminShopScreenHandler.SLOT_NEXT, control(Items.ARROW, "Next Page", List.of(Text.literal("Trang tiep"))));
        inventory.setStack(AdminShopScreenHandler.SLOT_CLOSE, control(Items.BARRIER, "Close", List.of(Text.literal("Dong shop"))));
    }

    private static void fillBorder(SimpleInventory inventory, ItemConvertible borderItem) {
        for (int slot = 0; slot < AdminShopScreenHandler.SIZE; slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) {
                inventory.setStack(slot, filler(borderItem));
            }
        }
    }

    private static void fillAll(SimpleInventory inventory, ItemConvertible item) {
        for (int slot = 0; slot < AdminShopScreenHandler.SIZE; slot++) {
            inventory.setStack(slot, filler(item));
        }
    }

    private static ItemStack categoryIcon(ShopCategory category) {
        ItemStack stack = new ItemStack(category.icon());
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(category.displayName()));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Click de mo danh muc"),
                Text.literal("So item: " + category.countItems())
        )));
        return stack;
    }

    private static ItemStack control(ItemConvertible item, String name, List<Text> lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return stack;
    }

    private static ItemStack searchIcon() {
        return control(Items.COMPASS, "Search", List.of(Text.literal("Click de tim item trong shop")));
    }

    private static ItemStack filler(ItemConvertible item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        return stack;
    }

    private static ItemStack pageInfo(int page, int totalPages) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Page " + (page + 1) + " / " + totalPages));
        return stack;
    }

    private static Item itemFromId(String id, Item fallback) {
        Identifier identifier = Identifier.tryParse(id == null || id.isBlank() ? "" : id);
        if (identifier == null) {
            return fallback;
        }
        return Registries.ITEM.getOrEmpty(identifier).orElse(fallback);
    }

    private static boolean matchesSearch(ServerPlayerEntity player, ShopEntry entry, String keyword) {
        if (keyword.isBlank()) {
            return true;
        }

        if (entry.id != null && entry.id.toLowerCase(Locale.ROOT).contains(keyword)) {
            return true;
        }
        if (ShopEntry.normalizeCategory(entry.category).toLowerCase(Locale.ROOT).contains(keyword)) {
            return true;
        }

        try {
            ItemStack stack = ItemStackSerializer.deserializeEntry(entry, player.server.getRegistryManager());
            return !stack.isEmpty() && stack.getName().getString().toLowerCase(Locale.ROOT).contains(keyword);
        } catch (Exception e) {
            AdminShopMod.LOGGER.error("Failed to search shop item {}", entry.id, e);
            return false;
        }
    }
}
