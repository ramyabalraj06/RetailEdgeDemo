package com.demo.retailedge.cart;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Commits exactly one scan batch, including its cursor and durable notification events. */
@Service
public class AbandonedCartBatchProcessor {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbandonedCartBatchProcessor.class);
	private final AbandonedCartRepository cartRepository;
	private final CartScanCheckpointRepository checkpointRepository;
	private final CartRecoveryTransitionService transitionService;
	private final CartNotificationOutboxService outboxService;
	private final VoucherCodeGenerator voucherCodeGenerator;
	private final Clock clock;
	private final int batchSize;
	private final BigDecimal voucherThreshold;

	public AbandonedCartBatchProcessor(
			AbandonedCartRepository cartRepository,
			CartScanCheckpointRepository checkpointRepository,
			CartRecoveryTransitionService transitionService,
			CartNotificationOutboxService outboxService,
			VoucherCodeGenerator voucherCodeGenerator,
			Clock clock,
			@Value("${cart.recovery.batch-size:100}") int batchSize,
			@Value("${cart.recovery.voucher-threshold:50.00}") BigDecimal voucherThreshold) {
		this.cartRepository = cartRepository;
		this.checkpointRepository = checkpointRepository;
		this.transitionService = transitionService;
		this.outboxService = outboxService;
		this.voucherCodeGenerator = voucherCodeGenerator;
		this.clock = clock;
		this.batchSize = batchSize;
		this.voucherThreshold = voucherThreshold;
	}

	/** Processes one batch in a new transaction and atomically advances its durable cursor. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public CartScanBatchResult processNextBatch(String storeId, Instant cutoff) {
		Instant now = clock.instant();
		CartScanCheckpoint checkpoint = checkpointRepository.findByStoreId(storeId)
				.orElseGet(() -> checkpointRepository.save(new CartScanCheckpoint(storeId, now)));
		long cursor = checkpoint.getLastSuccessfulCartId() == null ? 0L : checkpoint.getLastSuccessfulCartId();
		List<AbandonedCart> candidates = cartRepository
				.findByStoreIdAndCheckoutStatusAndRecoveryStatusAndAbandonmentTimestampIsNullAndLastActivityAtLessThanEqualAndIdGreaterThanOrderByIdAsc(
						storeId, CheckoutStatus.PENDING, RecoveryStatus.PENDING, cutoff, cursor,
						PageRequest.of(0, batchSize));
		if (candidates.isEmpty()) {
			checkpoint.restartCycle(now);
			checkpointRepository.saveAndFlush(checkpoint);
			return new CartScanBatchResult(0, 0, 0, null, true);
		}

		int notificationsQueued = 0;
		int superseded = 0;
		long lastProcessedId = cursor;
		for (AbandonedCart candidate : candidates) {
			AbandonedCart cart = cartRepository.findByIdAndStoreIdForUpdate(candidate.getId(), storeId)
					.orElse(null);
			if (cart == null || cart.getCheckoutStatus() != CheckoutStatus.PENDING
					|| cart.getRecoveryStatus() != RecoveryStatus.PENDING
					|| cart.getAbandonmentTimestamp() != null || cart.getLastActivityAt().isAfter(cutoff)) {
				lastProcessedId = candidate.getId();
				continue;
			}

			cart.markAbandoned(now);
			AbandonedCart newest = cartRepository
					.findFirstByStoreIdAndCustomerIdAndCheckoutStatusAndRecoveryStatusInAndLastActivityAtLessThanEqualOrderByLastActivityAtDescIdDesc(
							storeId, cart.getCustomerId(), CheckoutStatus.PENDING,
							List.of(RecoveryStatus.PENDING, RecoveryStatus.NOTIFIED), cutoff)
					.orElse(null);
			if (newest == null || !newest.getId().equals(cart.getId())) {
				transitionService.recovery(cart, RecoveryStatus.EXPIRED, "SUPERSEDED", now);
				superseded++;
			} else {
				expireOlderNotifications(cart, now);
				String voucherCode = cart.getCartTotal().compareTo(voucherThreshold) >= 0
						? voucherCodeGenerator.generate() : null;
				cart.setNotificationDetails(now, voucherCode);
				transitionService.recovery(cart, RecoveryStatus.NOTIFIED, "ABANDONED", now);
				cartRepository.saveAndFlush(cart);
				outboxService.enqueueRecoveryNotification(cart, now);
				notificationsQueued++;
			}
			cartRepository.saveAndFlush(cart);
			lastProcessedId = candidate.getId();
		}

		checkpoint.advanceTo(lastProcessedId, now);
		checkpointRepository.saveAndFlush(checkpoint);
		LOGGER.atInfo().addKeyValue("event", "cart_scan_batch_committed")
				.addKeyValue("storeId", storeId)
				.addKeyValue("cartsExamined", candidates.size())
				.addKeyValue("notificationsQueued", notificationsQueued)
				.addKeyValue("cartsSuperseded", superseded)
				.addKeyValue("lastSuccessfulCartId", lastProcessedId)
				.log("Abandoned cart batch committed");
		return new CartScanBatchResult(candidates.size(), notificationsQueued,
				superseded, lastProcessedId, false);
	}

	private void expireOlderNotifications(AbandonedCart newest, Instant now) {
		for (AbandonedCart previous : cartRepository.findByStoreIdAndCustomerIdAndRecoveryStatusAndIdNot(
				newest.getStoreId(), newest.getCustomerId(), RecoveryStatus.NOTIFIED, newest.getId())) {
			if (previous.getLastActivityAt().isBefore(newest.getLastActivityAt())) {
				transitionService.recovery(previous, RecoveryStatus.EXPIRED, "SUPERSEDED", now);
				cartRepository.save(previous);
			}
		}
	}
}