package com.demo.retailedge.cart;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Durable per-store keyset position for restartable abandonment scans. */
@Entity
@Table(name = "cart_scan_checkpoint")
public class CartScanCheckpoint {

	@Id
	@Column(name = "store_id", length = 100)
	private String storeId;

	@Column(name = "last_successful_cart_id")
	private Long lastSuccessfulCartId;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private Integer version;

	protected CartScanCheckpoint() {
	}

	public CartScanCheckpoint(String storeId, Instant updatedAt) {
		this.storeId = storeId;
		this.updatedAt = updatedAt;
	}

	public String getStoreId() { return storeId; }

	public Long getLastSuccessfulCartId() { return lastSuccessfulCartId; }

	public Integer getVersion() { return version; }

	void advanceTo(long cartId, Instant timestamp) {
		lastSuccessfulCartId = cartId;
		updatedAt = timestamp;
	}

	void restartCycle(Instant timestamp) {
		lastSuccessfulCartId = null;
		updatedAt = timestamp;
	}
}