package com.demo.retailedge.cart;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Coordinates independent scan transactions and resumes from the durable store cursor. */
@Service
public class AbandonedCartScanService {

	private final AbandonedCartBatchProcessor batchProcessor;
	private final CartScanCheckpointRepository checkpointRepository;
	private final Clock clock;
	private final long inactivityMinutes;
	private final int maxBatchesPerScan;

	public AbandonedCartScanService(
			AbandonedCartBatchProcessor batchProcessor,
			CartScanCheckpointRepository checkpointRepository,
			Clock clock,
			@Value("${cart.recovery.inactivity-minutes:30}") long inactivityMinutes,
			@Value("${cart.recovery.max-batches-per-scan:100}") int maxBatchesPerScan) {
		if (inactivityMinutes <= 0 || maxBatchesPerScan < 1) {
			throw new IllegalArgumentException("Cart recovery scan settings must be positive");
		}
		this.batchProcessor = batchProcessor;
		this.checkpointRepository = checkpointRepository;
		this.clock = clock;
		this.inactivityMinutes = inactivityMinutes;
		this.maxBatchesPerScan = maxBatchesPerScan;
	}

	/** Scans one authenticated store and commits each bounded batch independently. */
	public AbandonmentScanResponseDto scanStore(String storeId) {
		if (storeId == null || storeId.isBlank() || storeId.length() > 100) {
			throw new CartValidationException("Store ID is invalid");
		}
		Instant cutoff = clock.instant().minus(Duration.ofMinutes(inactivityMinutes));
		int batches = 0;
		int examined = 0;
		int queued = 0;
		int superseded = 0;
		boolean cycleComplete = false;
		boolean restartedFromBeginning = false;
		while (batches < maxBatchesPerScan) {
			CartScanBatchResult result = batchProcessor.processNextBatch(storeId, cutoff);
			if (result.cycleComplete() && result.cartsExamined() == 0) {
				if (!restartedFromBeginning) {
					restartedFromBeginning = true;
					continue;
				}
				cycleComplete = true;
				break;
			}
			batches++;
			examined += result.cartsExamined();
			queued += result.notificationsQueued();
			superseded += result.cartsSuperseded();
		}
		Long cursor = checkpointRepository.findById(storeId)
				.map(CartScanCheckpoint::getLastSuccessfulCartId).orElse(null);
		return new AbandonmentScanResponseDto(batches, examined, queued, superseded, cursor, cycleComplete);
	}
}