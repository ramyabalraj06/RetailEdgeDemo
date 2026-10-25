package com.demo.retailedge.cart;

import java.time.Instant;

/** Owner-visible recovery state without internal entities or unrelated cart data. */
public record RecoveryResponseDto(long cartId, CheckoutStatus checkoutStatus,
		RecoveryStatus recoveryStatus, Instant abandonmentTimestamp,
		String voucherCode, Instant recoveryTimestamp, Integer version) {

	public static RecoveryResponseDto from(AbandonedCart cart) {
		return new RecoveryResponseDto(cart.getId(), cart.getCheckoutStatus(), cart.getRecoveryStatus(),
				cart.getAbandonmentTimestamp(), cart.getVoucherCode(), cart.getRecoveryTimestamp(), cart.getVersion());
	}
}