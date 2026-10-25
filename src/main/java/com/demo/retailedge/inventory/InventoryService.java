package com.demo.retailedge.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {

	private static final Logger LOGGER = LoggerFactory.getLogger(InventoryService.class);

	private final InventoryRepository inventoryRepository;

	/** Creates the inventory service with its persistence dependency. */
	public InventoryService(InventoryRepository inventoryRepository) {
		this.inventoryRepository = inventoryRepository;
	}

	/** Verifies that a requested store matches the authenticated token's store claim. */
	public void assertStoreAccess(String requestedStoreId, String authenticatedStoreId) {
		validateStoreId(requestedStoreId);
		if (authenticatedStoreId == null || !requestedStoreId.equals(authenticatedStoreId)) {
			throw new StoreAccessDeniedException();
		}
	}

	/** Creates inventory for a product owned by the supplied store. */
	public Inventory create(String storeId, String productId, int stockQuantity) {
		validateStoreId(storeId);
		validateProductId(productId);
		validateStockQuantity(stockQuantity);
		if (inventoryRepository.existsByStoreIdAndProductId(storeId, productId)) {
			throw new InventoryConflictException("Inventory already exists for this store and product");
		}
		try {
			Inventory inventory = inventoryRepository.saveAndFlush(new Inventory(storeId, productId, stockQuantity));
			LOGGER.atInfo().addKeyValue("event", "inventory_created")
					.addKeyValue("storeId", storeId).addKeyValue("productId", productId)
					.log("Inventory created");
			return inventory;
		} catch (DataIntegrityViolationException exception) {
			throw new InventoryConflictException("Inventory already exists for this store and product");
		}
	}

	/** Sets the stock quantity when the caller's expected version is current. */
	public Inventory updateStock(String storeId, String productId, int newStockQuantity, long expectedVersion) {
		validateStoreId(storeId);
		validateProductId(productId);
		validateStockQuantity(newStockQuantity);
		validateVersion(expectedVersion);
		Inventory inventory = getByProduct(storeId, productId);
		if (inventory.getVersion() != expectedVersion) {
			throw new InventoryConflictException("Inventory was updated by another request");
		}
		inventory.setStockQuantity(newStockQuantity);
		try {
			Inventory updated = inventoryRepository.saveAndFlush(inventory);
			LOGGER.atInfo().addKeyValue("event", "inventory_stock_updated")
					.addKeyValue("storeId", storeId).addKeyValue("productId", productId)
					.addKeyValue("stockQuantity", newStockQuantity).log("Inventory stock updated");
			return updated;
		} catch (OptimisticLockingFailureException exception) {
			throw new InventoryConflictException("Inventory was updated by another request");
		}
	}

	@Transactional(readOnly = true)
	/** Returns inventory only if it belongs to the supplied store. */
	public Inventory getByProduct(String storeId, String productId) {
		validateStoreId(storeId);
		validateProductId(productId);
		return inventoryRepository.findByStoreIdAndProductId(storeId, productId)
				.orElseThrow(() -> new InventoryNotFoundException("Inventory not found"));
	}

	/** Deletes inventory if it belongs to the supplied store and expected version. */
	public void deleteByProduct(String storeId, String productId, long expectedVersion) {
		validateStoreId(storeId);
		validateProductId(productId);
		validateVersion(expectedVersion);
		Inventory inventory = getByProduct(storeId, productId);
		if (inventory.getVersion() != expectedVersion) {
			throw new InventoryConflictException("Inventory was updated by another request");
		}
		try {
			inventoryRepository.delete(inventory);
			inventoryRepository.flush();
			LOGGER.atInfo().addKeyValue("event", "inventory_deleted")
					.addKeyValue("storeId", storeId).addKeyValue("productId", productId)
					.log("Inventory deleted");
		} catch (OptimisticLockingFailureException exception) {
			throw new InventoryConflictException("Inventory was updated by another request");
		}
	}

	private void validateProductId(String productId) {
		if (productId == null || productId.isBlank() || productId.length() > 100) {
			throw new InventoryValidationException("Product ID must contain 1 to 100 characters");
		}
	}

	private void validateStoreId(String storeId) {
		if (storeId == null || storeId.isBlank() || storeId.length() > 100) {
			throw new InventoryValidationException("Store ID must contain 1 to 100 characters");
		}
	}

	private void validateStockQuantity(int stockQuantity) {
		if (stockQuantity < 0) {
			throw new InventoryValidationException("Stock quantity cannot be negative");
		}
	}

	private void validateVersion(long version) {
		if (version < 0) {
			throw new InventoryValidationException("Version cannot be negative");
		}
	}
}