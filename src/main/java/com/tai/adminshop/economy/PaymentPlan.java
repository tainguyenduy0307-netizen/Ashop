package com.tai.adminshop.economy;

/** Pure, deterministic currency split used by server checkout and unit tests. */
public record PaymentPlan(long sapphireSpent, long rubySpent) {
	/** Booster exception: Ruby is deliberately ignored even when it could cover a Sapphire shortfall. */
	public static PaymentPlan sapphireOnlyForPrice(double price, long sapphireBalance) {
		if (!Double.isFinite(price) || price < 0 || price != Math.rint(price) || price > Long.MAX_VALUE || sapphireBalance < 0) return null;
		long requested = (long) price;
		return sapphireBalance >= requested ? new PaymentPlan(requested, 0) : null;
	}
    public static PaymentPlan forPrice(String currency, double price, long sapphireBalance, long rubyBalance) {
        String normalized = Currency.normalize(currency);
        if ((!Currency.SAPPHIRE.equals(normalized) && !Currency.RUBY.equals(normalized))
                || !Double.isFinite(price) || price < 0 || price != Math.rint(price) || price > Long.MAX_VALUE
                || sapphireBalance < 0 || rubyBalance < 0) return null;
        long requested = (long) price;
        if (Currency.RUBY.equals(normalized)) return rubyBalance >= requested ? new PaymentPlan(0, requested) : null;
        long sapphire = Math.min(sapphireBalance, requested);
        long ruby = requested - sapphire;
        return rubyBalance >= ruby ? new PaymentPlan(sapphire, ruby) : null;
    }
}
