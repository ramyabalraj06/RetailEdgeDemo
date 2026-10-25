package com.demo.retailedge.inventory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Validates required RetailEdge token claims that are not covered by issuer/signature checks. */
public class InventoryJwtClaimsValidator implements OAuth2TokenValidator<Jwt> {

	private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
	private final String audience;
	private final Clock clock;

	/** Creates a claim validator for the configured audience and time source. */
	public InventoryJwtClaimsValidator(String audience, Clock clock) {
		this.audience = audience;
		this.clock = clock;
	}

	/** Validates required time, audience, store, role, and scope claims. */
	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null || jwt.getNotBefore() == null) {
			return invalid("AUTH_TOKEN_INVALID");
		}

		Instant now = clock.instant();
		if (!jwt.getExpiresAt().isAfter(now.minus(CLOCK_SKEW))) {
			return invalid("AUTH_TOKEN_EXPIRED");
		}
		if (jwt.getNotBefore().isAfter(now.plus(CLOCK_SKEW))) {
			return invalid("AUTH_TOKEN_NOT_YET_VALID");
		}
		if (jwt.getIssuedAt().isAfter(now.plus(CLOCK_SKEW))) {
			return invalid("AUTH_TOKEN_INVALID");
		}
		if (jwt.getAudience() == null || !jwt.getAudience().contains(audience)
				|| !hasText(jwt.getClaim("store_id")) || !hasText(jwt.getClaim("role"))
				|| !hasText(jwt.getClaim("scope"))) {
			return invalid("AUTH_TOKEN_INVALID");
		}
		return OAuth2TokenValidatorResult.success();
	}

	private boolean hasText(Object claim) {
		if (claim instanceof String value) {
			return !value.isBlank();
		}
		if (claim instanceof Collection<?> values) {
			return values.stream().anyMatch(value -> value instanceof String text && !text.isBlank());
		}
		return false;
	}

	private OAuth2TokenValidatorResult invalid(String code) {
		return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, "Required access token validation failed", null));
	}
}