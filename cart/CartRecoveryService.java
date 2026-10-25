package com.demo.retailedge.cart;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns recovery authorization, trusted order-event handling, and overdue state changes. */
@Service
public class CartRecoveryService {

	private static final Logger LOGGER = LoggerFactory.getLogger(CartRecoveryService.class);	
	private final AbandonedCartRepository cartRepository;
	private final ProcessedOrderEventRepository eventRepository;
	private final CartRecoveryTransitionService transitionService;
	private final CartRecoveryExpiryBatchProcessor expiryBatchProcessor;
	private final Clock clock;
	private final Duration conversionWindow;

	public CartRecoveryService(AbandonedCartRepository cartRepository,
			ProcessedOrderEventRepository eventRepository,
			CartRecoveryTransitionService transitionService,
			CartRecoveryExpiryBatchProcessor expiryBatchProcessor,
			Clock clock,
			@Value("${cart.recovery.conversion-window-hours:24}") long conversionWindowHours) {
		if (conversionWindowHours <= 0) {
			throw new IllegalArgumentException("Conversion window must be positive");
		}
		this.cartRepository = cartRepository;
		this.eventRepository = eventRepository;
		this.transitionService = transitionService;
		this.expiryBatchProcessor = expiryBatchProcessor;
		this.clock = clock;
		this.conversionWindow = Duration.ofHours(conversionWindowHours);
	}

	/** Returns recovery state only to the authenticated customer who owns the store-scoped cart. */
	@Transactional
	public RecoveryResponseDto getRecovery(String storeId, long cartId, String customerId) {
		validateIdentity(storeId, customerId);
		AbandonedCart cart = cartRepository.findByIdAndStoreId(cartId, storeId)
				.orElseThrow(CartNotFoundException::new);
		if (!cart.getCustomerId().equals(customerId)) {
			throw new CartForbiddenException();
		}
		expireIfOverdue(cart, clock.instant());
		return RecoveryResponseDto.from(cart);
	}

	/** Consumes a trusted order completion exactly once and verifies all cart ownership claims. */
	@Transactional
	public void handleOrderCompletion(OrderCompletedEvent event) {
		if (event == null || event.eventId() == null || event.eventId().isBlank()
				|| event.eventId().length() > 128 || event.completedAt() == null) {
			throw new CartValidationException("Order completion event is invalid");
		}
		validateIdentity(event.storeId(), event.customerId());
		if (event.cartId() == null || event.cartId() <= 0) {
			throw new CartValidationException("Cart ID is invalid");
		}
		AbandonedCart cart = cartRepository.findByIdAndStoreIdForUpdate(event.cartId(), event.storeId())
				.orElseThrow(CartNotFoundException::new);
		if (!cart.getCustomerId().equals(event.customerId())) {
			throw new CartForbiddenException();
		}
		if (eventRepository.existsById(event.eventId())) {
			return;
		}
		eventRepository.saveAndFlush(new ProcessedOrderEvent(event.eventId(), event.storeId(),
				event.cartId(), event.customerId(), clock.instant()));

		if (cart.getCheckoutStatus() == CheckoutStatus.PENDING) {
			transitionService.checkout(cart, CheckoutStatus.COMPLETED, "ORDER_COMPLETED", event.completedAt());
		}
		if (cart.getRecoveryStatus() == RecoveryStatus.NOTIFIED) {
			Instant deadline = cart.getRecoveryTimestamp().plus(conversionWindow);
			if (!event.completedAt().isBefore(cart.getRecoveryTimestamp()) && !event.completedAt().isAfter(deadline)) {
				transitionService.recovery(cart, RecoveryStatus.CONVERTED, "ORDER_CONVERTED", event.completedAt());
			} else if (event.completedAt().isAfter(deadline)) {
				transitionService.recovery(cart, RecoveryStatus.EXPIRED, "ORDER_AFTER_RECOVERY_WINDOW", event.completedAt());
			}
		}
		LOGGER.atInfo().addKeyValue("event", "cart_order_event_consumed")
				.addKeyValue("storeId", cart.getStoreId()).addKeyValue("cartId", cart.getId())
				.addKeyValue("eventId", event.eventId()).log("Trusted order event consumed");
	}

	/** Expires all overdue recoveries for one store using separate bounded transactions. */
	public int expireOverdueForStore(String storeId) {
		if (storeId == null || storeId.isBlank()) {
			throw new CartValidationException("Store ID is invalid");
		}
		Instant deadline = clock.instant().minus(conversionWindow);
		int expired = 0;
		int batch;
		do {
			batch = expiryBatchProcessor.expireBatch(storeId, deadline);
			expired += batch;
		} while (batch > 0);
		return expired;
	}

	private void expireIfOverdue(AbandonedCart cart, Instant now) {
		if (cart.getRecoveryStatus() == RecoveryStatus.NOTIFIED
				&& now.isAfter(cart.getRecoveryTimestamp().plus(conversionWindow))) {
			transitionService.recovery(cart, RecoveryStatus.EXPIRED, "RECOVERY_WINDOW_ELAPSED", now);
			cartRepository.save(cart);
		}
	}

	private void validateIdentity(String storeId, String customerId) {
		if (storeId == null || storeId.isBlank() || storeId.length() > 100
				|| customerId == null || customerId.isBlank() || customerId.length() > 100) {
			throw new CartValidationException("Store or customer identity is invalid");
		}
	}
}