package com.demo.retailedge.cart;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Internal maintenance task that expires overdue recoveries store by store. */
@Component
public class CartRecoveryExpiryScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(CartRecoveryExpiryScheduler.class);
	private final AbandonedCartRepository cartRepository;
	private final CartRecoveryService recoveryService;
	private final Clock clock;
	private final long conversionWindowHours;

	public CartRecoveryExpiryScheduler(AbandonedCartRepository cartRepository,
			CartRecoveryService recoveryService, Clock clock,
			@org.springframework.beans.factory.annotation.Value("${cart.recovery.conversion-window-hours:24}")
			long conversionWindowHours) {
		this.cartRepository = cartRepository;
		this.recoveryService = recoveryService;
		this.clock = clock;
		this.conversionWindowHours = conversionWindowHours;
	}

	@Scheduled(fixedDelayString = "${cart.recovery.expiry-poll-ms:60000}")
	public void expireOverdueRecoveries() {
		var deadline = clock.instant().minus(Duration.ofHours(conversionWindowHours));
		for (String storeId : cartRepository.findDistinctStoreIdByRecoveryStatusAndRecoveryTimestampLessThan(
				RecoveryStatus.NOTIFIED, deadline)) {
			try {
				int expired = recoveryService.expireOverdueForStore(storeId);
				if (expired > 0) {
					LOGGER.atInfo().addKeyValue("event", "cart_recoveries_expired")
							.addKeyValue("storeId", storeId).addKeyValue("count", expired)
							.log("Overdue cart recoveries expired");
				}
			} catch (RuntimeException exception) {
				LOGGER.atError().addKeyValue("event", "cart_recovery_expiry_failed")
						.addKeyValue("storeId", storeId).log("Cart recovery expiry failed");
			}
		}
	}
}