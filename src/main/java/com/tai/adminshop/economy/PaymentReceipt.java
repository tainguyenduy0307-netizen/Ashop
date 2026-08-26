package com.tai.adminshop.economy;

import java.math.BigDecimal;

public record PaymentReceipt(BigDecimal moneySpent, long sapphireSpent, long rubySpent) {
    public static PaymentReceipt none() { return new PaymentReceipt(BigDecimal.ZERO, 0, 0); }
}
