package com.demo.retailedge.cart;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Durable inbox marker that makes trusted order-completion events idempotent. */
@Entity
@Table(name = "processed_order_event")
public class ProcessedOrderEvent {

	@Id
	@Column(name = "event_id", length = 128)
	private String eventId;

	@Column(name = "store_id", nullable = false, length = 100)
	private String storeId;

	@Column(name = "cart_id", nullable = false)
	private Long cartId;

	@Column(name = "customer_id", nullable = false, length = 100)
	private String customerId;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	protected ProcessedOrderEvent() {
	}

	public ProcessedOrderEvent(String eventId, String storeId, Long cartId,
			String customerId, Instant processedAt) {
		this.eventId = eventId;
		this.storeId = storeId;
		this.cartId = cartId;
		this.customerId = customerId;
		this.processedAt = processedAt;
	}

	public String getEventId() { return eventId; }
}