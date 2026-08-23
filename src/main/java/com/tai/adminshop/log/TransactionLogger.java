package com.tai.adminshop.log;

import com.tai.adminshop.AdminShopMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import com.tai.adminshop.util.PriceFormatter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class TransactionLogger {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path LOG_DIR = FabricLoader.getInstance().getConfigDir().resolve("adminshop").resolve("logs");
    private static final Path LOG_FILE = LOG_DIR.resolve("transactions.log");

    private TransactionLogger() {
    }

    public static void buy(ServerPlayerEntity player, String item, int amount, double price) {
        write("[BUY]\nplayer=" + player.getName().getString() + "\nitem=" + item + "\namount=" + amount + "\nprice=" + PriceFormatter.money(price));
    }

    public static void sell(ServerPlayerEntity player, String item, int amount, double price) {
        write("[SELL]\nplayer=" + player.getName().getString() + "\nitem=" + item + "\namount=" + amount + "\nprice=" + PriceFormatter.money(price));
    }

    public static void sellAll(ServerPlayerEntity player, int totalItems, double totalMoney) {
        write("[SELL_ALL]\nplayer=" + player.getName().getString() + "\ntotalItems=" + totalItems + "\ntotalMoney=" + PriceFormatter.money(totalMoney));
    }

    private static synchronized void write(String body) {
        try {
            Files.createDirectories(LOG_DIR);
            String line = "---- " + LocalDateTime.now().format(FORMATTER) + " ----\n" + body + "\n";
            Files.writeString(LOG_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            AdminShopMod.LOGGER.error("Failed to write AdminShop transaction log", e);
        }
    }
}
