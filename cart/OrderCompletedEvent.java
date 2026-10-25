package com.demo.retailedge.cart;

import java.time.Instant;

/** Trusted internal event emitted only after the order transaction commits. */
public record OrderCompletedEvent(String eventId, String storeId, Long cartId,
		String customerId, Instant completedAt) {
}