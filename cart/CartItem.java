package com.demo.retailedge.cart;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.hibernate.annotations.Check;

/** Immutable-value representation of a cart line item. */
@Embeddable
@Check(constraints = "quantity > 0 and unit_price >= 0")
public class CartItem {

	@Column(name = "product_id", nullable = false, length = 100)
	private String productId;

	@Column(name = "quantity", nullable = false)
	private int quantity;

	@Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
	private BigDecimal unitPrice;

	protected CartItem() {
	}

	/** Creates a validated cart item value. */
	public CartItem(String productId, int quantity, BigDecimal unitPrice) {
		this.productId = productId;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	/** Returns the item product identifier. */
	public String getProductId() {
		return productId;
	}

	/** Returns the item quantity. */
	public int getQuantity() {
		return quantity;
	}

	/** Returns the exact decimal unit price. */
	public BigDecimal getUnitPrice() {
		return unitPrice;
	}
}