package com.demo.retailedge.inventory;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InventoryRepository inventoryRepository;

	@BeforeEach
	void clearInventory() {
		inventoryRepository.deleteAll();
	}

	@Test
	void supportsInventoryCrudThroughDtos() throws Exception {
		mockMvc.perform(post("/api/stores/store-1/inventory")
					.with(admin("store-1"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"productId\":\"SKU-API\",\"stockQuantity\":10}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.storeId").value("store-1"))
				.andExpect(jsonPath("$.productId").value("SKU-API"))
				.andExpect(jsonPath("$.version").value(0));

		mockMvc.perform(put("/api/stores/store-1/inventory/SKU-API/stock")
					.with(admin("store-1"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"stockQuantity\":7,\"expectedVersion\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stockQuantity").value(7))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(get("/api/stores/store-1/inventory/SKU-API").with(admin("store-1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stockQuantity").value(7));

		mockMvc.perform(delete("/api/stores/store-1/inventory/SKU-API")
					.param("version", "1").with(admin("store-1")))
				.andExpect(status().isNoContent());
	}

	@Test
	void requiresAuthenticationAndReturnsMachineReadable401() throws Exception {
		mockMvc.perform(get("/api/stores/store-1/inventory/missing"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void rejectsAuthenticatedUsersWithoutInventoryRole() throws Exception {
		mockMvc.perform(get("/api/stores/store-1/inventory/missing").with(userWithRole("store-1", "STORE_USER")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN_INVENTORY_ROLE"));
	}

	@Test
	void deniesCrossStoreAccess() throws Exception {
		mockMvc.perform(get("/api/stores/store-2/inventory/SKU-API").with(admin("store-1")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN_RESOURCE"));
	}

	@Test
	void rejectsNegativeStockWithMachineReadableValidationError() throws Exception {
		mockMvc.perform(post("/api/stores/store-1/inventory")
					.with(admin("store-1"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"productId\":\"SKU-NEG\",\"stockQuantity\":-1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsOversizedPathParametersAcrossAllInventoryMethods() throws Exception {
		String oversized = "x".repeat(101);

		mockMvc.perform(post("/api/stores/{storeId}/inventory", oversized)
					.with(admin(oversized))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"productId\":\"SKU-LONG\",\"stockQuantity\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

		mockMvc.perform(get("/api/stores/store-1/inventory/{productId}", oversized)
					.with(admin("store-1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

		mockMvc.perform(put("/api/stores/store-1/inventory/{productId}/stock", oversized)
					.with(admin("store-1"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"stockQuantity\":1,\"expectedVersion\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

		mockMvc.perform(delete("/api/stores/store-1/inventory/{productId}", oversized)
					.param("version", "0").with(admin("store-1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	void rejectsUnknownWriteFieldsRatherThanBindingEntityProperties() throws Exception {
		mockMvc.perform(post("/api/stores/store-1/inventory")
					.with(admin("store-1"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"productId\":\"SKU-MASS\",\"stockQuantity\":1,\"storeId\":\"store-2\",\"version\":99}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
		org.junit.jupiter.api.Assertions.assertFalse(inventoryRepository.existsByStoreIdAndProductId("store-1", "SKU-MASS"));
	}

	@Test
	void returnsMachineReadableNotFoundError() throws Exception {
		mockMvc.perform(get("/api/stores/store-1/inventory/missing").with(admin("store-1")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("INVENTORY_NOT_FOUND"));
	}

	@Test
	void rejectsStaleVersionWithConflict() throws Exception {
		inventoryRepository.saveAndFlush(new Inventory("store-1", "SKU-STALE", 4));
		mockMvc.perform(put("/api/stores/store-1/inventory/SKU-STALE/stock")
					.with(admin("store-1"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"stockQuantity\":3,\"expectedVersion\":8}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("INVENTORY_CONFLICT"));
	}

	private RequestPostProcessor admin(String storeId) {
		return userWithRole(storeId, "ADMIN");
	}

	private RequestPostProcessor userWithRole(String storeId, String role) {
		return jwt().jwt(token -> token.claim("store_id", storeId)
				.claim("role", role).claim("scope", "inventory:manage"))
				.authorities(new SimpleGrantedAuthority("ROLE_" + role),
						new SimpleGrantedAuthority("SCOPE_inventory:manage"));
	}
}