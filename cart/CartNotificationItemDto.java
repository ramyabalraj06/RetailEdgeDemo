package com.demo.retailedge.cart;

import java.math.BigDecimal;

public record CartNotificationItemDto(String productId, int quantity, BigDecimal unitPrice) {
	static CartNotificationItemDto from(CartItem item) {
		return new CartNotificationItemDto(item.getProductId(), item.getQuantity(), item.getUnitPrice());
	}
}