package com.demo.retailedge.inventory;

/** Read representation of inventory without exposing JPA entity binding. */
public record InventoryResponseDto(long id, String storeId, String productId, int stockQuantity, long version) {

	/** Maps a persisted inventory entity to its public response representation. */
	public static InventoryResponseDto from(Inventory inventory) {
		return new InventoryResponseDto(inventory.getId(), inventory.getStoreId(), inventory.getProductId(),
				inventory.getStockQuantity(), inventory.getVersion());
	}
}