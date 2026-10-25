package com.demo.retailedge.cart;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRecoveryOutboxRepository extends JpaRepository<CartRecoveryOutboxEvent, Long> {

	boolean existsByIdempotencyKey(String idempotencyKey);

	List<CartRecoveryOutboxEvent> findByDeliveryStatusOrderByIdAsc(String deliveryStatus, Pageable pageable);
}