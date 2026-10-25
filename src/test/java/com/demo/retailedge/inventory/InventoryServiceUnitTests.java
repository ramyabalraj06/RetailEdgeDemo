package com.demo.retailedge.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class InventoryServiceUnitTests {

	@Mock
	private InventoryRepository inventoryRepository;

	@InjectMocks
	private InventoryService inventoryService;

	@Test
	void createsInventory() {
		when(inventoryRepository.existsByStoreIdAndProductId("store-1", "SKU-1")).thenReturn(false);
		when(inventoryRepository.saveAndFlush(any(Inventory.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		Inventory created = inventoryService.create("store-1", "SKU-1", 4);

		assertEquals("store-1", created.getStoreId());
		assertEquals(4, created.getStockQuantity());
	}

	@Test
	void rejectsDuplicateAndDatabaseRaceOnCreate() {
		when(inventoryRepository.existsByStoreIdAndProductId("store-1", "SKU-1")).thenReturn(true);
		assertThrows(InventoryConflictException.class, () -> inventoryService.create("store-1", "SKU-1", 4));

		when(inventoryRepository.existsByStoreIdAndProductId("store-1", "SKU-2")).thenReturn(false);
		when(inventoryRepository.saveAndFlush(any(Inventory.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate"));
		assertThrows(InventoryConflictException.class, () -> inventoryService.create("store-1", "SKU-2", 4));
	}

	@Test
	void rejectsInvalidStoreProductStockAndVersionValues() {
		assertThrows(InventoryValidationException.class, () -> inventoryService.create(" ", "SKU-1", 1));
		assertThrows(InventoryValidationException.class, () -> inventoryService.create("store-1", " ", 1));
		assertThrows(InventoryValidationException.class, () -> inventoryService.create("store-1", "SKU-1", -1));
		assertThrows(InventoryValidationException.class,
				() -> inventoryService.updateStock("store-1", "SKU-1", 1, -1));
		assertThrows(InventoryValidationException.class,
				() -> inventoryService.deleteByProduct("store-1", "SKU-1", -1));
	}

	@Test
	void rejectsRequestsForAnotherAuthenticatedStore() {
		assertThrows(StoreAccessDeniedException.class,
				() -> inventoryService.assertStoreAccess("store-1", "store-2"));
		assertThrows(StoreAccessDeniedException.class,
				() -> inventoryService.assertStoreAccess("store-1", null));
	}

	@Test
	void updatesStockAndMapsConcurrentWriteFailure() {
		Inventory inventory = new Inventory("store-1", "SKU-1", 4);
		when(inventoryRepository.findByStoreIdAndProductId("store-1", "SKU-1"))
				.thenReturn(Optional.of(inventory));
		when(inventoryRepository.saveAndFlush(inventory)).thenReturn(inventory);

		assertEquals(2, inventoryService.updateStock("store-1", "SKU-1", 2, 0).getStockQuantity());

		Inventory concurrentInventory = new Inventory("store-1", "SKU-2", 4);
		when(inventoryRepository.findByStoreIdAndProductId("store-1", "SKU-2"))
				.thenReturn(Optional.of(concurrentInventory));
		when(inventoryRepository.saveAndFlush(concurrentInventory))
				.thenThrow(new ObjectOptimisticLockingFailureException(Inventory.class, 2L));
		assertThrows(InventoryConflictException.class,
				() -> inventoryService.updateStock("store-1", "SKU-2", 2, 0));
	}

	@Test
	void mapsMissingInventoryAndDeletesWithVersion() {
		when(inventoryRepository.findByStoreIdAndProductId("store-1", "missing"))
				.thenReturn(Optional.empty());
		assertThrows(InventoryNotFoundException.class,
				() -> inventoryService.getByProduct("store-1", "missing"));

		Inventory inventory = new Inventory("store-1", "SKU-3", 4);
		when(inventoryRepository.findByStoreIdAndProductId("store-1", "SKU-3"))
				.thenReturn(Optional.of(inventory));
		doNothing().when(inventoryRepository).delete(inventory);
		doNothing().when(inventoryRepository).flush();

		inventoryService.deleteByProduct("store-1", "SKU-3", 0);
		verify(inventoryRepository).delete(inventory);
	}

	@Test
	void rejectsStaleDeleteAndMapsConcurrentDeleteFailure() {
		Inventory inventory = new Inventory("store-1", "SKU-4", 4);
		when(inventoryRepository.findByStoreIdAndProductId("store-1", "SKU-4"))
				.thenReturn(Optional.of(inventory));
		assertThrows(InventoryConflictException.class,
				() -> inventoryService.deleteByProduct("store-1", "SKU-4", 1));

		when(inventoryRepository.findByStoreIdAndProductId("store-1", "SKU-5"))
				.thenReturn(Optional.of(new Inventory("store-1", "SKU-5", 4)));
		org.mockito.Mockito.doThrow(new ObjectOptimisticLockingFailureException(Inventory.class, 5L))
				.when(inventoryRepository).flush();
		assertThrows(InventoryConflictException.class,
				() -> inventoryService.deleteByProduct("store-1", "SKU-5", 0));
	}
}