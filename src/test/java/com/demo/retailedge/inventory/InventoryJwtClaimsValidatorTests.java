package com.demo.retailedge.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class InventoryJwtClaimsValidatorTests {

	private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
	private InventoryJwtClaimsValidator validator;

	@BeforeEach
	void setUp() {
		validator = new InventoryJwtClaimsValidator("retailedge-test", Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void acceptsAllRequiredClaims() {
		OAuth2TokenValidatorResult result = validator.validate(validToken().build());
		assertTrue(result.getErrors().isEmpty());
	}

	@Test
	void rejectsMissingTimeOrIdentityClaims() {
		Jwt token = Jwt.withTokenValue("test")
				.header("alg", "RS256")
				.audience(List.of("retailedge-test"))
				.claim("store_id", "store-1")
				.claim("role", "ADMIN")
				.claim("scope", "inventory:manage")
				.build();

		assertEquals("AUTH_TOKEN_INVALID", validator.validate(token).getErrors().get(0).getErrorCode());
	}

	@Test
	void rejectsExpiredTokensWithSpecificCode() {
		Jwt token = validToken().expiresAt(NOW.minusSeconds(31)).build();
		assertEquals("AUTH_TOKEN_EXPIRED", validator.validate(token).getErrors().get(0).getErrorCode());
	}

	@Test
	void rejectsNotYetValidTokensWithSpecificCode() {
		Jwt token = validToken().notBefore(NOW.plusSeconds(31)).build();
		assertEquals("AUTH_TOKEN_NOT_YET_VALID", validator.validate(token).getErrors().get(0).getErrorCode());
	}

	@Test
	void rejectsWrongAudienceOrMissingStoreScopeClaims() {
		Jwt wrongAudience = validToken().audience(List.of("other-api")).build();
		assertEquals("AUTH_TOKEN_INVALID", validator.validate(wrongAudience).getErrors().get(0).getErrorCode());

		Jwt missingStore = validToken().claim("store_id", " ").build();
		assertEquals("AUTH_TOKEN_INVALID", validator.validate(missingStore).getErrors().get(0).getErrorCode());
	}

	@Test
	void decoderRejectsAlgNoneWhenPinnedToRs256() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		RSAPublicKey publicKey = (RSAPublicKey) generator.generateKeyPair().getPublic();
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
				.signatureAlgorithm(SignatureAlgorithm.RS256)
				.build();
		String header = encode("{\"alg\":\"none\",\"typ\":\"JWT\"}");
		String payload = encode("{\"sub\":\"test\"}");

		assertThrows(JwtException.class, () -> decoder.decode(header + "." + payload + "."));
	}

	private Jwt.Builder validToken() {
		return Jwt.withTokenValue("test")
				.header("alg", "RS256")
				.issuer("https://issuer.test")
				.audience(List.of("retailedge-test"))
				.issuedAt(NOW.minusSeconds(60))
				.notBefore(NOW.minusSeconds(30))
				.expiresAt(NOW.plusSeconds(300))
				.claim("store_id", "store-1")
				.claim("role", "ADMIN")
				.claim("scope", "inventory:manage");
	}

	private String encode(String value) {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}
}