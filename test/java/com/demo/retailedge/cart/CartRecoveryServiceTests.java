package com.demo.retailedge.cart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(CartRecoveryTestConfiguration.class)
class CartRecoveryServiceTests {

	private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

	@Autowired private CartService cartService;
	@Autowired private AbandonedCartScanService scanService;
	@Autowired private AbandonedCartBatchProcessor batchProcessor;
	@Autowired private CartRecoveryService recoveryService;
	@Autowired private AbandonedCartRepository cartRepository;
	@Autowired private CartScanCheckpointRepository checkpointRepository;
	@Autowired private CartRecoveryOutboxRepository outboxRepository;
	@Autowired private CartRecoveryTransitionRepository transitionRepository;
	@Autowired private ProcessedOrderEventRepository orderEventRepository;
	@Autowired private MutableTestClock clock;

	@BeforeEach
	void clearData() {
		outboxRepository.deleteAll();
		transitionRepository.deleteAll();
		orderEventRepository.deleteAll();
		cartRepository.deleteAll();
		checkpointRepository.deleteAll();
		clock.setInstant(NOW);
	}

	@Test
	void marksCartAbandonedAtInclusiveThirtyMinuteBoundary() {
		AbandonedCart cart = createCart("store-1", "customer-1", "session-1", "60.00",
				NOW.minus(Duration.ofMinutes(30)));

		AbandonmentScanResponseDto result = scanService.scanStore("store-1");

		AbandonedCart updated = cartRepository.findByIdAndStoreId(cart.getId(), "store-1").orElseThrow();
		assertEquals(1, result.notificationsQueued());
		assertEquals(RecoveryStatus.NOTIFIED, updated.getRecoveryStatus());
		assertEquals(NOW, updated.getAbandonmentTimestamp());
	}

	@Test
	void generatesVoucherAtOrAboveConfiguredThreshold() {
		AbandonedCart cart = createCart("store-1", "customer-2", "session-2", "50.00",
				NOW.minus(Duration.ofMinutes(31)));
		scanService.scanStore("store-1");

		AbandonedCart updated = cartRepository.findByIdAndStoreId(cart.getId(), "store-1").orElseThrow();
		assertNotNull(updated.getVoucherCode());
		assertEquals(1, outboxRepository.count());
		org.junit.jupiter.api.Assertions.assertTrue(outboxRepository.findAll().get(0).getPayload()
				.contains(updated.getVoucherCode()));
	}

	@Test
	void queuesNotificationWithoutVoucherBelowThreshold() {
		AbandonedCart cart = createCart("store-1", "customer-3", "session-3", "49.99",
				NOW.minus(Duration.ofMinutes(30)));
		scanService.scanStore("store-1");

		AbandonedCart updated = cartRepository.findByIdAndStoreId(cart.getId(), "store-1").orElseThrow();
		assertNull(updated.getVoucherCode());
		assertEquals(1, outboxRepository.count());
		org.junit.jupiter.api.Assertions.assertTrue(outboxRepository.findAll().get(0).getPayload()
				.contains("\"voucherCode\":null"));
	}

	@Test
	void convertsOrderAtInclusiveTwentyFourHourBoundaryAndDeduplicatesEvent() {
		AbandonedCart cart = notifiedCart("customer-4", "session-4");
		Instant notifiedAt = cart.getRecoveryTimestamp();
		OrderCompletedEvent event = new OrderCompletedEvent("order-event-1", "store-1", cart.getId(),
				"customer-4", notifiedAt.plus(Duration.ofHours(24)));

		recoveryService.handleOrderCompletion(event);
		recoveryService.handleOrderCompletion(event);

		AbandonedCart updated = cartRepository.findByIdAndStoreId(cart.getId(), "store-1").orElseThrow();
		assertEquals(RecoveryStatus.CONVERTED, updated.getRecoveryStatus());
		assertEquals(CheckoutStatus.COMPLETED, updated.getCheckoutStatus());
		assertEquals(1, orderEventRepository.count());
	}

	@Test
	void expiresRecoveryAfterTwentyFourHours() {
		AbandonedCart cart = notifiedCart("customer-5", "session-5");
		clock.setInstant(cart.getRecoveryTimestamp().plus(Duration.ofHours(24)).plusSeconds(1));

		assertEquals(1, recoveryService.expireOverdueForStore("store-1"));
		assertEquals(RecoveryStatus.EXPIRED,
				cartRepository.findByIdAndStoreId(cart.getId(), "store-1").orElseThrow().getRecoveryStatus());
	}

	@Test
	void onlyNewestAbandonedCartTriggersAcrossOneItemBatches() {
		AbandonedCart older = createCart("store-1", "customer-6", "session-6a", "60.00",
				NOW.minus(Duration.ofHours(2)));
		AbandonedCart newest = createCart("store-1", "customer-6", "session-6b", "70.00",
				NOW.minus(Duration.ofMinutes(31)));

		AbandonmentScanResponseDto result = scanService.scanStore("store-1");

		assertEquals(1, result.notificationsQueued());
		assertEquals(1, result.cartsSuperseded());
		assertEquals(RecoveryStatus.EXPIRED,
				cartRepository.findByIdAndStoreId(older.getId(), "store-1").orElseThrow().getRecoveryStatus());
		assertEquals(RecoveryStatus.NOTIFIED,
				cartRepository.findByIdAndStoreId(newest.getId(), "store-1").orElseThrow().getRecoveryStatus());
		assertEquals(1, outboxRepository.count());
	}

	@Test
	void resumesFromDurableCursorAfterCommittedBatch() {
		createCart("store-1", "customer-7", "session-7a", "30.00", NOW.minus(Duration.ofHours(2)));
		createCart("store-1", "customer-8", "session-7b", "30.00", NOW.minus(Duration.ofHours(2)));

		CartScanBatchResult first = batchProcessor.processNextBatch("store-1", NOW.minus(Duration.ofMinutes(30)));
		assertEquals(1, first.cartsExamined());
		assertNotNull(checkpointRepository.findById("store-1").orElseThrow().getLastSuccessfulCartId());

		scanService.scanStore("store-1");

		assertEquals(2, outboxRepository.count());
		assertNull(checkpointRepository.findById("store-1").orElseThrow().getLastSuccessfulCartId());
	}

	@Test
	void recoveryReadDeniesAnotherCustomer() {
		AbandonedCart cart = createCart("store-1", "customer-owner", "session-9", "10.00",
				NOW.minus(Duration.ofMinutes(10)));
		assertThrows(CartForbiddenException.class,
				() -> recoveryService.getRecovery("store-1", cart.getId(), "customer-other"));
	}

	private AbandonedCart notifiedCart(String customerId, String sessionId) {
		AbandonedCart cart = createCart("store-1", customerId, sessionId, "80.00",
				NOW.minus(Duration.ofMinutes(30)));
		scanService.scanStore("store-1");
		return cartRepository.findByIdAndStoreId(cart.getId(), "store-1").orElseThrow();
	}

	private AbandonedCart createCart(String storeId, String customerId, String sessionId,
			String price, Instant lastActivity) {
		return cartService.createCart(storeId, customerId, sessionId,
				List.of(new CartItem("SKU-1", 1, new BigDecimal(price))), "USD", lastActivity);
	}
}