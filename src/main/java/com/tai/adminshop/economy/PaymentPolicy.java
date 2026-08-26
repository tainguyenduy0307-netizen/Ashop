package com.tai.adminshop.economy;

import net.minecraft.server.network.ServerPlayerEntity;

import java.math.BigDecimal;

/** Shared authoritative checkout policy for Shop and Store purchases. */
public final class PaymentPolicy {
    private PaymentPolicy() { }

    public static Result charge(ServerPlayerEntity player, String suppliedCurrency, double price) {
		return charge(player, suppliedCurrency, price, "DEFAULT");
	}

	public static Result charge(ServerPlayerEntity player, String suppliedCurrency, double price, String paymentMode) {
        String currency = Currency.normalize(suppliedCurrency);
        if (currency.isEmpty()) return Result.failure("Invalid currency");
        if (!Double.isFinite(price) || price < 0) return Result.failure("Invalid price");
        if (Currency.MONEY.equals(currency)) {
            if (!CobEcoHook.hasMoney(player.getUuid(), price) || !CobEcoHook.takeMoney(player.getUuid(), price)) return Result.failure("Insufficient Money");
            return Result.success(new PaymentReceipt(BigDecimal.valueOf(price), 0, 0));
        }
        if (Math.rint(price) != price || price > Long.MAX_VALUE) return Result.failure("Sapphire and Ruby prices must be whole positive integers");
        OptionalBalance sapphire = balance(player, Currency.SAPPHIRE);
		if ("SAPPHIRE_ONLY".equalsIgnoreCase(paymentMode)) {
			if (!Currency.SAPPHIRE.equals(currency) || !sapphire.available) return Result.failure("Required Sapphire provider is unavailable");
			PaymentPlan plan=PaymentPlan.sapphireOnlyForPrice(price,sapphire.value);if(plan==null)return Result.failure("Insufficient Sapphire");
			long amount=plan.sapphireSpent();
			if(amount>0&&!UnovaCoreEconomyBridge.withdraw(player.getUuid(),Currency.SAPPHIRE,amount))return Result.failure("Could not take Sapphire");
			return Result.success(new PaymentReceipt(BigDecimal.ZERO,amount,0));
		}
        OptionalBalance ruby = balance(player, Currency.RUBY);
        if ((!Currency.RUBY.equals(currency) && !sapphire.available) || !ruby.available) return Result.failure("Required UnovaCore currency provider is unavailable");
        PaymentPlan plan = PaymentPlan.forPrice(currency, price, sapphire.value, ruby.value);
        if (plan == null) return Result.failure(Currency.RUBY.equals(currency) ? "Insufficient Ruby" : "Insufficient Sapphire/Ruby");
        long sapphireSpent = plan.sapphireSpent();
        long rubySpent = plan.rubySpent();
        if (sapphireSpent > 0 && !UnovaCoreEconomyBridge.withdraw(player.getUuid(), Currency.SAPPHIRE, sapphireSpent)) return Result.failure("Could not take Sapphire");
        if (rubySpent > 0 && !UnovaCoreEconomyBridge.withdraw(player.getUuid(), Currency.RUBY, rubySpent)) {
            if (sapphireSpent > 0) UnovaCoreEconomyBridge.deposit(player.getUuid(), Currency.SAPPHIRE, sapphireSpent);
            return Result.failure("Could not take Ruby");
        }
        return Result.success(new PaymentReceipt(BigDecimal.ZERO, sapphireSpent, rubySpent));
    }

    public static boolean refund(ServerPlayerEntity player, PaymentReceipt receipt) {
        boolean success = true;
        if (receipt.moneySpent().signum() > 0) success &= CobEcoHook.giveMoney(player.getUuid(), receipt.moneySpent().doubleValue());
        if (receipt.sapphireSpent() > 0) success &= UnovaCoreEconomyBridge.deposit(player.getUuid(), Currency.SAPPHIRE, receipt.sapphireSpent());
        if (receipt.rubySpent() > 0) success &= UnovaCoreEconomyBridge.deposit(player.getUuid(), Currency.RUBY, receipt.rubySpent());
        return success;
    }

    public static boolean canReceiveSellPayout(ServerPlayerEntity player, String suppliedCurrency, double amount) {
        String currency = Currency.normalize(suppliedCurrency);
        if (currency.isEmpty() || Currency.RUBY.equals(currency) || !Double.isFinite(amount) || amount < 0) return false;
        if (Currency.MONEY.equals(currency)) return true;
        return amount == Math.rint(amount) && UnovaCoreEconomyBridge.balance(player.getUuid(), Currency.SAPPHIRE).isPresent();
    }

    public static boolean giveSellPayout(ServerPlayerEntity player, String suppliedCurrency, double amount) {
        String currency = Currency.normalize(suppliedCurrency);
        if (!canReceiveSellPayout(player, currency, amount)) return false;
        return Currency.MONEY.equals(currency)
                ? CobEcoHook.giveMoney(player.getUuid(), amount)
                : UnovaCoreEconomyBridge.deposit(player.getUuid(), Currency.SAPPHIRE, (long) amount);
    }

    public static boolean reverseSellPayout(ServerPlayerEntity player, String suppliedCurrency, double amount) {
        String currency = Currency.normalize(suppliedCurrency);
        if (currency.isEmpty() || Currency.RUBY.equals(currency) || !Double.isFinite(amount) || amount < 0) return false;
        return Currency.MONEY.equals(currency)
                ? CobEcoHook.takeMoney(player.getUuid(), amount)
                : amount == Math.rint(amount) && UnovaCoreEconomyBridge.withdraw(player.getUuid(), Currency.SAPPHIRE, (long) amount);
    }

    private static OptionalBalance balance(ServerPlayerEntity player, String currency) {
        var value = UnovaCoreEconomyBridge.balance(player.getUuid(), currency);
        return value.isPresent() ? new OptionalBalance(true, value.getAsLong()) : new OptionalBalance(false, 0);
    }
    private record OptionalBalance(boolean available, long value) { }
    public record Result(boolean successful, String message, PaymentReceipt receipt) {
        static Result success(PaymentReceipt receipt) { return new Result(true, "", receipt); }
        static Result failure(String message) { return new Result(false, message, PaymentReceipt.none()); }
    }
}
