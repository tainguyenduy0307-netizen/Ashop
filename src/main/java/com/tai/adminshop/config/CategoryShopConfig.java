package com.tai.adminshop.config;

import java.util.ArrayList;
import java.util.List;

public class CategoryShopConfig {
    public String id;
    public String displayName;
    public String title;
    public int size = 54;
    public String icon = "minecraft:barrel";
    public String fillItem = "minecraft:gray_stained_glass_pane";
    public Buttons buttons = new Buttons();
    public List<ShopEntry> items = new ArrayList<>();

    public static class Buttons {
        public int previousPage = 45;
        public int back = 49;
        public int nextPage = 53;
    }
}
