package com.demo.retailedge.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP endpoints for authenticated cart recovery operations. */
@RestController
@Validated
public class CartRecoveryController {

	private final AbandonedCartScanService scanService;
	private final CartRecoveryService recoveryService;
	private final RecoveryAnalyticsService analyticsService;

	public CartRecoveryController(AbandonedCartScanService scanService,
			CartRecoveryService recoveryService, RecoveryAnalyticsService analyticsService) {
		this.scanService = scanService;
		this.recoveryService = recoveryService;
		this.analyticsService = analyticsService;
	}

	/** Starts the bounded, restartable scan for the authenticated administrator's store. */
	@PostMapping("/scan/abandoned-carts")
	public AbandonmentScanResponseDto scanAbandonedCarts(@AuthenticationPrincipal Jwt jwt) {
		return scanService.scanStore(jwt.getClaimAsString("store_id"));
	}

	/** Returns recovery state only to the customer who owns the store-scoped cart. */
	@GetMapping("/carts/{id}/recovery")
	public RecoveryResponseDto getCartRecovery(@PathVariable @Min(1) long id,
			@AuthenticationPrincipal Jwt jwt) {
		return recoveryService.getRecovery(jwt.getClaimAsString("store_id"), id,
				jwt.getClaimAsString("customer_id"));
	}

	/** Returns store-scoped recovery analytics using an opaque keyset cursor. */
	@GetMapping("/analytics/recovery")
	public RecoveryAnalyticsResponseDto getRecoveryAnalytics(
			@RequestParam("from") @NotBlank String from,
			@RequestParam("to") @NotBlank String to,
			@RequestParam(value = "cursor", required = false) String cursor,
			@RequestParam(value = "limit", defaultValue = "50") @Min(1) @Max(100) int limit,
			@AuthenticationPrincipal Jwt jwt) {
		return analyticsService.getRecoveryAnalytics(jwt.getClaimAsString("store_id"), from, to, cursor, limit);
	}
}