package com.demo.retailedge.cart;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** Immutable audit row for cart checkout or recovery state changes. */
@Entity
@Table(name = "cart_recovery_transition",
		indexes = @Index(name = "idx_cart_transition_analytics",
				columnList = "store_id, state_family, changed_at, id"))
public class CartRecoveryTransition {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "store_id", nullable = false, length = 100)
	private String storeId;

	@Column(name = "cart_id", nullable = false)
	private Long cartId;

	@Column(name = "state_family", nullable = false, length = 16)
	private String stateFamily;

	@Column(name = "from_state", length = 24)
	private String fromState;

	@Column(name = "to_state", nullable = false, length = 24)
	private String toState;

	@Column(name = "reason", nullable = false, length = 40)
	private String reason;

	@Column(name = "changed_at", nullable = false)
	private Instant changedAt;

	protected CartRecoveryTransition() {
	}

	public CartRecoveryTransition(String storeId, Long cartId, String stateFamily,
			String fromState, String toState, String reason, Instant changedAt) {
		this.storeId = storeId;
		this.cartId = cartId;
		this.stateFamily = stateFamily;
		this.fromState = fromState;
		this.toState = toState;
		this.reason = reason;
		this.changedAt = changedAt;
	}

	public Long getId() { return id; }

	public String getStoreId() { return storeId; }

	public Long getCartId() { return cartId; }

	public String getStateFamily() { return stateFamily; }

	public String getFromState() { return fromState; }

	public String getToState() { return toState; }

	public String getReason() { return reason; }

	public Instant getChangedAt() { return changedAt; }
}