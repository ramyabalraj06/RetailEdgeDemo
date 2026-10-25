package com.demo.retailedge.cart;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Expires a bounded page of overdue recoveries in its own transaction. */
@Service
public class CartRecoveryExpiryBatchProcessor {

	private final AbandonedCartRepository cartRepository;
	private final CartRecoveryTransitionService transitionService;
	private final Clock clock;
	private final int batchSize;

	public CartRecoveryExpiryBatchProcessor(AbandonedCartRepository cartRepository,
			CartRecoveryTransitionService transitionService, Clock clock,
			@org.springframework.beans.factory.annotation.Value("${cart.recovery.expiry-batch-size:100}") int batchSize) {
		this.cartRepository = cartRepository;
		this.transitionService = transitionService;
		this.clock = clock;
		this.batchSize = batchSize;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int expireBatch(String storeId, Instant deadline) {
		List<AbandonedCart> overdue = cartRepository
				.findByStoreIdAndRecoveryStatusAndRecoveryTimestampLessThanAndIdGreaterThanOrderByIdAsc(
						storeId, RecoveryStatus.NOTIFIED, deadline, 0L, PageRequest.of(0, batchSize));
		Instant now = clock.instant();
		for (AbandonedCart cart : overdue) {
			transitionService.recovery(cart, RecoveryStatus.EXPIRED, "RECOVERY_WINDOW_ELAPSED", now);
			cartRepository.save(cart);
		}
		return overdue.size();
	}
}