package com.demo.retailedge.cart;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Records recovery and checkout state changes in the same transaction as the cart update. */
@Service
public class CartRecoveryTransitionService {

	private static final Logger LOGGER = LoggerFactory.getLogger(CartRecoveryTransitionService.class);
	private final CartRecoveryTransitionRepository transitionRepository;

	public CartRecoveryTransitionService(CartRecoveryTransitionRepository transitionRepository) {
		this.transitionRepository = transitionRepository;
	}

	public void recovery(AbandonedCart cart, RecoveryStatus next, String reason, Instant at) {
		RecoveryStatus previous = cart.getRecoveryStatus();
		if (previous == next) {
			return;
		}
		cart.markRecoveryStatus(next);
		transitionRepository.save(new CartRecoveryTransition(cart.getStoreId(), cart.getId(),
				"RECOVERY", previous.name(), next.name(), reason, at));
		logTransition(cart, "RECOVERY", previous.name(), next.name(), reason);
	}

	public void checkout(AbandonedCart cart, CheckoutStatus next, String reason, Instant at) {
		CheckoutStatus previous = cart.getCheckoutStatus();
		if (previous == next) {
			return;
		}
		cart.markCheckoutCompleted();
		transitionRepository.save(new CartRecoveryTransition(cart.getStoreId(), cart.getId(),
				"CHECKOUT", previous.name(), next.name(), reason, at));
		logTransition(cart, "CHECKOUT", previous.name(), next.name(), reason);
	}

	private void logTransition(AbandonedCart cart, String family, String from, String to, String reason) {
		LOGGER.atInfo().addKeyValue("event", "cart_state_transition")
				.addKeyValue("storeId", cart.getStoreId())
				.addKeyValue("cartId", cart.getId())
				.addKeyValue("stateFamily", family)
				.addKeyValue("fromState", from)
				.addKeyValue("toState", to)
				.addKeyValue("reason", reason)
				.log("Cart state transitioned");
	}
}