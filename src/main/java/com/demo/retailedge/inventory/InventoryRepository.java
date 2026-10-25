package com.demo.retailedge.inventory;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

	/** Finds a product's inventory only within its owning store. */
	Optional<Inventory> findByStoreIdAndProductId(String storeId, String productId);

	/** Checks for a product inventory row within its owning store. */
	boolean existsByStoreIdAndProductId(String storeId, String productId);

	/** Deletes a product inventory row only within its owning store. */
	void deleteByStoreIdAndProductId(String storeId, String productId);
}