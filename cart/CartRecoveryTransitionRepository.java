package com.demo.retailedge.cart;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRecoveryTransitionRepository extends JpaRepository<CartRecoveryTransition, Long>,
		CartRecoveryAnalyticsRepositoryCustom {
}