package com.demo.retailedge.cart;

import java.time.Instant;
import java.util.List;

public interface CartRecoveryAnalyticsRepositoryCustom {

	List<CartRecoveryTransition> findRecoveryTransitions(
			String storeId, Instant from, Instant to, Long afterId, int maxResults);
}