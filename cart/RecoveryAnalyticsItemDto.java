package com.demo.retailedge.cart;

import java.time.Instant;

public record RecoveryAnalyticsItemDto(long transitionId, long cartId, String fromState,
		String toState, String reason, Instant changedAt) {

	public static RecoveryAnalyticsItemDto from(CartRecoveryTransition transition) {
		return new RecoveryAnalyticsItemDto(transition.getId(), transition.getCartId(),
				transition.getFromState(), transition.getToState(), transition.getReason(), transition.getChangedAt());
	}
}