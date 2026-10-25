package com.demo.retailedge.cart;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/** Generates high-entropy voucher codes; uniqueness is additionally enforced by the database. */
@Component
public class VoucherCodeGenerator {

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		byte[] bytes = new byte[18];
		secureRandom.nextBytes(bytes);
		return "REC-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toUpperCase(java.util.Locale.ROOT);
	}
}