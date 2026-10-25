package com.demo.retailedge.cart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates cart ownership and item values and calculates totals using exact decimal arithmetic. */
@Service
public class CartService {

	private final AbandonedCartRepository cartRepository;
	private final CartScanCheckpointRepository checkpointRepository;

	public CartService(AbandonedCartRepository cartRepository, CartScanCheckpointRepository checkpointRepository) {
		this.cartRepository = cartRepository;
		this.checkpointRepository = checkpointRepository;
	}

	/** Creates a pending cart for an authenticated upstream cart/checkout integration. */
	@Transactional
	public AbandonedCart createCart(String storeId, String customerId, String sessionId,
			List<CartItem> items, String currency, Instant lastActivityAt) {
		validateIdentifier(storeId, 100, "Store ID");
		validateIdentifier(customerId, 100, "Customer ID");
		validateIdentifier(sessionId, 128, "Session ID");
		if (items == null || items.isEmpty() || items.size() > 100) {
			throw new CartValidationException("Cart must contain 1 to 100 items");
		}
		if (currency == null || !currency.trim().matches("(?i)[a-z]{3}")) {
			throw new CartValidationException("Currency must be a three-letter code");
		}
		if (lastActivityAt == null) {
			throw new CartValidationException("Last activity time is required");
		}

		List<CartItem> normalizedItems = new ArrayList<>();
		BigDecimal total = BigDecimal.ZERO;
		for (CartItem item : items) {
			if (item == null) {
				throw new CartValidationException("Cart item cannot be null");
			}
			validateIdentifier(item.getProductId(), 100, "Product ID");
			if (item.getQuantity() <= 0 || item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) {
				throw new CartValidationException("Cart item quantity and unit price are invalid");
			}
			BigDecimal unitPrice = item.getUnitPrice().setScale(2, RoundingMode.HALF_UP);
			normalizedItems.add(new CartItem(item.getProductId(), item.getQuantity(), unitPrice));
			total = total.add(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
		}
		total = total.setScale(2, RoundingMode.HALF_UP);
		AbandonedCart cart = new AbandonedCart(storeId, customerId, sessionId, normalizedItems,
				total, currency.trim().toUpperCase(Locale.ROOT), lastActivityAt);
		try {
			AbandonedCart saved = cartRepository.saveAndFlush(cart);
			checkpointRepository.findById(storeId)
					.orElseGet(() -> checkpointRepository.save(new CartScanCheckpoint(storeId, lastActivityAt)));
			return saved;
		} catch (DataIntegrityViolationException exception) {
			throw new CartConflictException("Cart session already exists");
		}
	}

	private void validateIdentifier(String value, int maxLength, String label) {
		if (value == null || value.isBlank() || value.length() > maxLength) {
			throw new CartValidationException(label + " must contain 1 to " + maxLength + " characters");
		}
	}
}