package com.demo.retailedge.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartRecoveryNotificationPayload(String customerId, long cartId,
		List<CartNotificationItemDto> items, BigDecimal cartTotal, String currency, String voucherCode) {
	static CartRecoveryNotificationPayload from(AbandonedCart cart) {
		return new CartRecoveryNotificationPayload(cart.getCustomerId(), cart.getId(),
				cart.getItems().stream().map(CartNotificationItemDto::from).toList(),
				cart.getCartTotal(), cart.getCurrency(), cart.getVoucherCode());
	}
}