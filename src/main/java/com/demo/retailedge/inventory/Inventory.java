package com.demo.retailedge.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import org.hibernate.annotations.Check;

/**
 * Store-owned inventory for one product. The composite product index supports scoped lookups;
 * the store/stock index supports low-stock and availability queries within a store.
 */
@Entity
@Table(name = "inventory",
		uniqueConstraints = @UniqueConstraint(name = "uk_inventory_store_product",
				columnNames = { "store_id", "product_id" }),
		indexes = {
				@jakarta.persistence.Index(name = "idx_inventory_store_product",
						columnList = "store_id, product_id"),
				@jakarta.persistence.Index(name = "idx_inventory_store_stock",
						columnList = "store_id, stock_quantity")
		})
@Check(constraints = "stock_quantity >= 0")
public class Inventory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "store_id", nullable = false, length = 100)
	private String storeId;

	@Column(name = "product_id", nullable = false, length = 100)
	private String productId;

	@Column(name = "stock_quantity", nullable = false)
	private int stockQuantity;

	@Version
	@Column(nullable = false)
	private long version;

	protected Inventory() {
	}

	/** Creates an inventory row for the given store and product. */
	public Inventory(String storeId, String productId, int stockQuantity) {
		this.storeId = storeId;
		this.productId = productId;
		this.stockQuantity = stockQuantity;
	}

	/** Returns the database identifier. */
	public Long getId() {
		return id;
	}

	/** Returns the owning store identifier. */
	public String getStoreId() {
		return storeId;
	}

	/** Returns the product identifier within the owning store. */
	public String getProductId() {
		return productId;
	}

	/** Returns the available stock quantity. */
	public int getStockQuantity() {
		return stockQuantity;
	}

	/** Returns the optimistic-lock version. */
	public long getVersion() {
		return version;
	}

	void setStockQuantity(int stockQuantity) {
		this.stockQuantity = stockQuantity;
	}
}