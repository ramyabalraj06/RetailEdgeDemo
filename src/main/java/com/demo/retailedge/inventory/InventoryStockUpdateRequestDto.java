package com.demo.retailedge.inventory;

import jakarta.validation.constraints.PositiveOrZero;

/** Whitelisted request fields for an optimistic-lock-protected stock update. */
public record InventoryStockUpdateRequestDto(
		@PositiveOrZero int stockQuantity,
		@PositiveOrZero long expectedVersion) {
}