package com.tai.adminshop.config;

import java.util.ArrayList;
import java.util.List;

public class StoreEntry {
    public String id;
    public int slot = -1;
    public String displayItem = "minecraft:stone";
    public String name;
    public List<String> lore = new ArrayList<>();
    public double price;
    public String currency = "ruby";
    public String requiredGroup;
    public String track = "ranks";
    public List<String> commands = new ArrayList<>();
    public boolean giveItem;
    public String itemData;
}
