package com.demo.retailedge.cart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import org.hibernate.annotations.Check;

/** Store-owned cart with separate checkout and recovery lifecycle state. */
@Entity
@Table(name = "abandoned_cart",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_cart_store_session", columnNames = { "store_id", "session_id" }),
				@UniqueConstraint(name = "uk_cart_voucher_code", columnNames = "voucher_code")
		},
		indexes = {
				@Index(name = "idx_cart_scan_cursor",
						columnList = "store_id, checkout_status, recovery_status, last_activity_at, id"),
				@Index(name = "idx_cart_customer_recency",
						columnList = "store_id, customer_id, last_activity_at, id"),
				@Index(name = "idx_cart_recovery_deadline",
						columnList = "store_id, recovery_status, recovery_timestamp, id")
		})
@Check(constraints = "cart_total >= 0")
public class AbandonedCart {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "store_id", nullable = false, length = 100)
	private String storeId;

	@Column(name = "customer_id", nullable = false, length = 100)
	private String customerId;

	@Column(name = "session_id", nullable = false, length = 128)
	private String sessionId;

	@ElementCollection
	@CollectionTable(name = "abandoned_cart_item", joinColumns = @JoinColumn(name = "cart_id"),
			indexes = @Index(name = "idx_cart_item_cart", columnList = "cart_id"))
	@OrderColumn(name = "item_position")
	private List<CartItem> items = new ArrayList<>();

	@Column(name = "cart_total", nullable = false, precision = 19, scale = 2)
	private BigDecimal cartTotal;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "last_activity_at", nullable = false)
	private Instant lastActivityAt;

	@Column(name = "checkout_status", nullable = false, length = 24)
	@Enumerated(EnumType.STRING)
	private CheckoutStatus checkoutStatus;

	@Column(name = "abandonment_timestamp")
	private Instant abandonmentTimestamp;

	@Column(name = "recovery_status", nullable = false, length = 24)
	@Enumerated(EnumType.STRING)
	private RecoveryStatus recoveryStatus;

	@Column(name = "voucher_code", unique = true, length = 64)
	private String voucherCode;

	@Column(name = "recovery_timestamp")
	private Instant recoveryTimestamp;

	@Version
	@Column(nullable = false)
	private Integer version;

	protected AbandonedCart() {
	}

	/** Creates a cart from service-validated ownership and monetary values. */
	public AbandonedCart(String storeId, String customerId, String sessionId, List<CartItem> items,
			BigDecimal cartTotal, String currency, Instant lastActivityAt) {
		this.storeId = storeId;
		this.customerId = customerId;
		this.sessionId = sessionId;
		this.items = new ArrayList<>(items);
		this.cartTotal = cartTotal;
		this.currency = currency;
		this.lastActivityAt = lastActivityAt;
		this.checkoutStatus = CheckoutStatus.PENDING;
		this.recoveryStatus = RecoveryStatus.PENDING;
	}

	/** Returns the cart database identifier. */
	public Long getId() { return id; }

	/** Returns the owning store identifier. */
	public String getStoreId() { return storeId; }

	/** Returns the owning customer identifier. */
	public String getCustomerId() { return customerId; }

	/** Returns the originating session identifier. */
	public String getSessionId() { return sessionId; }

	/** Returns an immutable view of cart items. */
	public List<CartItem> getItems() { return List.copyOf(items); }

	/** Returns the service-calculated cart total. */
	public BigDecimal getCartTotal() { return cartTotal; }

	/** Returns the ISO 4217 currency code. */
	public String getCurrency() { return currency; }

	/** Returns the last cart activity instant. */
	public Instant getLastActivityAt() { return lastActivityAt; }

	/** Returns checkout state independently from recovery state. */
	public CheckoutStatus getCheckoutStatus() { return checkoutStatus; }

	/** Returns when the cart first qualified as abandoned. */
	public Instant getAbandonmentTimestamp() { return abandonmentTimestamp; }

	/** Returns the current recovery state. */
	public RecoveryStatus getRecoveryStatus() { return recoveryStatus; }

	/** Returns the optional one-time voucher code. */
	public String getVoucherCode() { return voucherCode; }

	/** Returns the recovery notification instant, if one was queued. */
	public Instant getRecoveryTimestamp() { return recoveryTimestamp; }

	/** Returns the optimistic-lock version. */
	public Integer getVersion() { return version; }

	void markAbandoned(Instant timestamp) {
		if (abandonmentTimestamp == null) {
			abandonmentTimestamp = timestamp;
		}
	}

	void setNotificationDetails(Instant timestamp, String code) {
		recoveryTimestamp = timestamp;
		voucherCode = code;
	}

	void markRecoveryStatus(RecoveryStatus status) {
		recoveryStatus = status;
	}

	void markCheckoutCompleted() {
		checkoutStatus = CheckoutStatus.COMPLETED;
	}
}