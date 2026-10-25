package com.demo.retailedge.cart;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

/** Durable notification event; an email/push adapter consumes pending rows idempotently. */
@Entity
@Table(name = "cart_recovery_outbox",
		uniqueConstraints = @UniqueConstraint(name = "uk_cart_outbox_idempotency", columnNames = "idempotency_key"),
		indexes = @Index(name = "idx_cart_outbox_delivery", columnList = "delivery_status, id"))
public class CartRecoveryOutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "store_id", nullable = false, length = 100)
	private String storeId;

	@Column(name = "cart_id", nullable = false)
	private Long cartId;

	@Column(name = "recipient_customer_id", nullable = false, length = 100)
	private String recipientCustomerId;

	@Column(name = "idempotency_key", nullable = false, length = 160)
	private String idempotencyKey;

	@Column(name = "event_type", nullable = false, length = 64)
	private String eventType;

	@Column(name = "payload", nullable = false, columnDefinition = "text")
	private String payload;

	@Column(name = "delivery_status", nullable = false, length = 16)
	private String deliveryStatus;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Version
	@Column(nullable = false)
	private Integer version;

	protected CartRecoveryOutboxEvent() {
	}

	public CartRecoveryOutboxEvent(String storeId, Long cartId, String recipientCustomerId,
			String idempotencyKey, String eventType, String payload, Instant createdAt) {
		this.storeId = storeId;
		this.cartId = cartId;
		this.recipientCustomerId = recipientCustomerId;
		this.idempotencyKey = idempotencyKey;
		this.eventType = eventType;
		this.payload = payload;
		this.createdAt = createdAt;
		this.deliveryStatus = "PENDING";
	}

	public Long getId() { return id; }

	public String getStoreId() { return storeId; }

	public Long getCartId() { return cartId; }

	public String getIdempotencyKey() { return idempotencyKey; }

	public String getEventType() { return eventType; }

	public String getPayload() { return payload; }

	public String getDeliveryStatus() { return deliveryStatus; }

	public Instant getCreatedAt() { return createdAt; }

	public int getAttemptCount() { return attemptCount; }

	public Integer getVersion() { return version; }

	void markDelivered() { deliveryStatus = "DELIVERED"; }

	void recordAttempt() { attemptCount++; }
}