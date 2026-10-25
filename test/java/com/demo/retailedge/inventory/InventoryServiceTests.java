package com.demo.retailedge.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class InventoryServiceTests {

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private InventoryRepository inventoryRepository;

	@BeforeEach
	void clearInventory() {
		inventoryRepository.deleteAll();
	}

	@Test
	void createsUpdatesReadsAndDeletesInventory() {
		Inventory created = inventoryService.create("store-1", "SKU-1", 12);
		assertEquals(12, created.getStockQuantity());

		Inventory updated = inventoryService.updateStock("store-1", "SKU-1", 7, created.getVersion());
		assertEquals(7, updated.getStockQuantity());
		assertEquals("SKU-1", inventoryService.getByProduct("store-1", "SKU-1").getProductId());

		inventoryService.deleteByProduct("store-1", "SKU-1", updated.getVersion());
		assertThrows(InventoryNotFoundException.class,
				() -> inventoryService.getByProduct("store-1", "SKU-1"));
	}

	@Test
	void rejectsNegativeStock() {
		assertThrows(InventoryValidationException.class,
				() -> inventoryService.create("store-1", "SKU-2", -1));
	}

	@Test
	void rejectsDuplicateProductInventory() {
		inventoryService.create("store-1", "SKU-3", 1);
		assertThrows(InventoryConflictException.class,
				() -> inventoryService.create("store-1", "SKU-3", 2));
	}

	@Test
	void allowsSameProductInDifferentStores() {
		inventoryService.create("store-1", "SKU-4", 2);
		inventoryService.create("store-2", "SKU-4", 5);

		assertEquals(2, inventoryService.getByProduct("store-1", "SKU-4").getStockQuantity());
		assertEquals(5, inventoryService.getByProduct("store-2", "SKU-4").getStockQuantity());
	}

	@Test
	void rejectsStaleStockVersion() {
		Inventory created = inventoryService.create("store-1", "SKU-5", 8);
		inventoryService.updateStock("store-1", "SKU-5", 7, created.getVersion());

		assertThrows(InventoryConflictException.class,
				() -> inventoryService.updateStock("store-1", "SKU-5", 6, created.getVersion()));
		assertEquals(7, inventoryService.getByProduct("store-1", "SKU-5").getStockQuantity());
	}

	@Test
	void hidesInventoryFromOtherStores() {
		inventoryService.create("store-1", "SKU-6", 3);

		assertThrows(InventoryNotFoundException.class,
				() -> inventoryService.getByProduct("store-2", "SKU-6"));
	}
}