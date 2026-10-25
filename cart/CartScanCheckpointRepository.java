package com.demo.retailedge.cart;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface CartScanCheckpointRepository extends JpaRepository<CartScanCheckpoint, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	java.util.Optional<CartScanCheckpoint> findByStoreId(String storeId);
}