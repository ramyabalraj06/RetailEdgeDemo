package com.demo.retailedge.cart;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CartRecoveryTestConfiguration.class)
class CartRecoveryApiTests {

	private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

	@Autowired private MockMvc mockMvc;
	@Autowired private CartService cartService;
	@Autowired private AbandonedCartRepository cartRepository;
	@Autowired private CartRecoveryTransitionRepository transitionRepository;
	@Autowired private CartRecoveryOutboxRepository outboxRepository;
	@Autowired private CartScanCheckpointRepository checkpointRepository;
	@Autowired private ProcessedOrderEventRepository orderEventRepository;

	@BeforeEach
	void clearData() {
		outboxRepository.deleteAll();
		transitionRepository.deleteAll();
		orderEventRepository.deleteAll();
		cartRepository.deleteAll();
		checkpointRepository.deleteAll();
	}

	@Test
	void nonAdminCannotTriggerScanAndGetsMachineReadableForbidden() throws Exception {
		mockMvc.perform(post("/scan/abandoned-carts").with(customer("store-1", "customer-1")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN_ADMIN_ONLY"));
	}

	@Test
	void adminCanTriggerStoreScopedScan() throws Exception {
		mockMvc.perform(post("/scan/abandoned-carts").with(admin("store-1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cartsExamined").value(0))
				.andExpect(jsonPath("$.cycleComplete").value(true));
	}

	@Test
	void crossCustomerRecoveryReturns403() throws Exception {
		AbandonedCart cart = cartService.createCart("store-1", "customer-b", "session-api",
				List.of(new CartItem("SKU-1", 1, new BigDecimal("1.00"))), "USD", NOW);

		mockMvc.perform(get("/carts/{id}/recovery", cart.getId())
					.with(customer("store-1", "customer-a")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN_RESOURCE"));
	}

	@Test
	void analyticsIsAdminOnlyAndUsesCursorLimitResponse() throws Exception {
		transitionRepository.saveAndFlush(new CartRecoveryTransition("store-1", 10L,
				"RECOVERY", "PENDING", "NOTIFIED", "ABANDONED", NOW.minusSeconds(10)));
		transitionRepository.saveAndFlush(new CartRecoveryTransition("store-1", 11L,
				"RECOVERY", "NOTIFIED", "CONVERTED", "ORDER_CONVERTED", NOW.minusSeconds(5)));
		String from = "2026-10-25T11:00:00Z";
		String to = "2026-10-25T13:00:00Z";

		mockMvc.perform(get("/analytics/recovery").param("from", from).param("to", to).param("limit", "1")
					.with(admin("store-1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.nextCursor").isNotEmpty())
				.andExpect(jsonPath("$.hasMore").value(true));

		mockMvc.perform(get("/analytics/recovery").param("from", from).param("to", to)
					.with(customer("store-1", "customer-a")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("FORBIDDEN_ADMIN_ONLY"));
	}

	@Test
	void rejectsInvalidAnalyticsDateRangeWithMachineReadableError() throws Exception {
		mockMvc.perform(get("/analytics/recovery")
					.param("from", "2026-10-25T13:00:00Z")
					.param("to", "2026-10-25T12:00:00Z")
					.with(admin("store-1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	private JwtRequestPostProcessor admin(String storeId) {
		return jwt().jwt(token -> token.claim("store_id", storeId)
				.claim("role", "ADMIN").claim("scope", "cart-recovery:manage"))
				.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),
						new SimpleGrantedAuthority("SCOPE_cart-recovery:manage"));
	}

	private JwtRequestPostProcessor customer(String storeId, String customerId) {
		return jwt().jwt(token -> token.claim("store_id", storeId)
				.claim("customer_id", customerId).claim("role", "CUSTOMER")
				.claim("scope", "cart:recovery:read"))
				.authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"),
						new SimpleGrantedAuthority("SCOPE_cart:recovery:read"));
	}
}