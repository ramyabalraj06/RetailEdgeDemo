package com.demo.retailedge.cart;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class CartNotificationOutboxService {

	private final CartRecoveryOutboxRepository outboxRepository;
	private final ObjectMapper objectMapper;

	public CartNotificationOutboxService(CartRecoveryOutboxRepository outboxRepository, ObjectMapper objectMapper) {
		this.outboxRepository = outboxRepository;
		this.objectMapper = objectMapper;
	}

	public void enqueueRecoveryNotification(AbandonedCart cart, java.time.Instant at) {
		String idempotencyKey = "cart-recovery:" + cart.getStoreId() + ":" + cart.getId();
		if (outboxRepository.existsByIdempotencyKey(idempotencyKey)) {
			return;
		}
		String payload = objectMapper.writeValueAsString(CartRecoveryNotificationPayload.from(cart));
		outboxRepository.save(new CartRecoveryOutboxEvent(cart.getStoreId(), cart.getId(),
				cart.getCustomerId(), idempotencyKey, "CART_RECOVERY_EMAIL_PUSH", payload, at));
	}
}