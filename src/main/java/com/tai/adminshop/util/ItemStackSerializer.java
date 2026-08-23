package com.tai.adminshop.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tai.adminshop.config.ShopEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

public final class ItemStackSerializer {
    private ItemStackSerializer() {
    }

    public static String serialize(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        ItemStack copy = stack.copyWithCount(1);
        NbtElement encoded = copy.encode(registries);
        return encoded.toString();
    }

    public static ItemStack deserialize(String itemData, RegistryWrapper.WrapperLookup registries) throws CommandSyntaxException {
        NbtCompound nbt = StringNbtReader.parse(itemData);
        return ItemStack.fromNbt(registries, nbt).orElse(ItemStack.EMPTY).copyWithCount(1);
    }

    public static ItemStack deserializeEntry(ShopEntry entry, RegistryWrapper.WrapperLookup registries) throws CommandSyntaxException {
        if (entry.itemData != null && !entry.itemData.isBlank()) {
            return deserialize(entry.itemData, registries);
        }
        if (entry.material != null && !entry.material.isBlank()) {
            Identifier identifier = Identifier.tryParse(entry.material);
            if (identifier != null) {
                return Registries.ITEM.getOrEmpty(identifier)
                        .map(item -> new ItemStack(item, 1))
                        .orElse(ItemStack.EMPTY);
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack fromItemId(String id) {
        Identifier identifier = Identifier.tryParse(id == null || id.isBlank() ? "" : id);
        if (identifier == null) {
            return ItemStack.EMPTY;
        }
        return Registries.ITEM.getOrEmpty(identifier)
                .map(item -> new ItemStack(item, 1))
                .orElse(ItemStack.EMPTY);
    }

    public static boolean matches(ItemStack candidate, ItemStack template) {
        return !candidate.isEmpty() && ItemStack.areItemsAndComponentsEqual(candidate, template);
    }
}
