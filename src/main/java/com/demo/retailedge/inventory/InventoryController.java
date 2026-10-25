package com.demo.retailedge.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP API for store-scoped inventory operations. */
@RestController
@RequestMapping("/api/stores/{storeId}/inventory")
@Validated
public class InventoryController {

	private final InventoryService inventoryService;

	public InventoryController(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
	}

	/** Creates inventory for a product in the authenticated store. */
	@PostMapping
	public ResponseEntity<InventoryResponseDto> create(
			@PathVariable @NotBlank @Size(max = 100) String storeId,
			@Valid @RequestBody InventoryCreateRequestDto request,
			@AuthenticationPrincipal Jwt jwt) {
		verifyStoreAccess(storeId, jwt);
		Inventory inventory = inventoryService.create(storeId, request.productId(), request.stockQuantity());
		return ResponseEntity.status(HttpStatus.CREATED).body(InventoryResponseDto.from(inventory));
	}

	/** Returns a product's inventory within the authenticated store. */
	@GetMapping("/{productId}")
	public InventoryResponseDto getByProduct(
			@PathVariable @NotBlank @Size(max = 100) String storeId,
			@PathVariable @NotBlank @Size(max = 100) String productId,
			@AuthenticationPrincipal Jwt jwt) {
		verifyStoreAccess(storeId, jwt);
		return InventoryResponseDto.from(inventoryService.getByProduct(storeId, productId));
	}

	/** Sets stock to the requested quantity if the supplied version is current. */
	@PutMapping("/{productId}/stock")
	public InventoryResponseDto updateStock(
			@PathVariable @NotBlank @Size(max = 100) String storeId,
			@PathVariable @NotBlank @Size(max = 100) String productId,
			@Valid @RequestBody InventoryStockUpdateRequestDto request,
			@AuthenticationPrincipal Jwt jwt) {
		verifyStoreAccess(storeId, jwt);
		return InventoryResponseDto.from(inventoryService.updateStock(
				storeId, productId, request.stockQuantity(), request.expectedVersion()));
	}

	/** Deletes inventory if the supplied version is current. */
	@DeleteMapping("/{productId}")
	public ResponseEntity<Void> deleteByProduct(
			@PathVariable @NotBlank @Size(max = 100) String storeId,
			@PathVariable @NotBlank @Size(max = 100) String productId,
			@RequestParam("version") @jakarta.validation.constraints.PositiveOrZero long version,
			@AuthenticationPrincipal Jwt jwt) {
		verifyStoreAccess(storeId, jwt);
		inventoryService.deleteByProduct(storeId, productId, version);
		return ResponseEntity.noContent().build();
	}

	private void verifyStoreAccess(String storeId, Jwt jwt) {
		inventoryService.assertStoreAccess(storeId, jwt == null ? null : jwt.getClaimAsString("store_id"));
	}
}