package com.demo.retailedge.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Whitelisted request fields for creating a store inventory record. */
public record InventoryCreateRequestDto(
		@NotBlank @Size(max = 100) String productId,
		@PositiveOrZero int stockQuantity) {
}