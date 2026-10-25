package com.demo.retailedge.cart;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;

/** Validates and serves store-scoped keyset-paginated recovery analytics. */
@Service
public class RecoveryAnalyticsService {

	private final CartRecoveryTransitionRepository transitionRepository;

	public RecoveryAnalyticsService(CartRecoveryTransitionRepository transitionRepository) {
		this.transitionRepository = transitionRepository;
	}

	/** Returns a bounded recovery transition page using an opaque ID cursor. */
	public RecoveryAnalyticsResponseDto getRecoveryAnalytics(String storeId, String fromValue,
			String toValue, String cursor, int limit) {
		if (storeId == null || storeId.isBlank() || storeId.length() > 100) {
			throw new CartValidationException("Store ID is invalid");
		}
		if (limit < 1 || limit > 100) {
			throw new CartValidationException("Limit must be between 1 and 100");
		}
		Instant from = parseInstant(fromValue, "from");
		Instant to = parseInstant(toValue, "to");
		if (!from.isBefore(to)) {
			throw new CartValidationException("from must be earlier than to");
		}
		Long afterId = decodeCursor(cursor);
		List<CartRecoveryTransition> results = transitionRepository.findRecoveryTransitions(
				storeId, from, to, afterId, limit + 1);
		boolean hasMore = results.size() > limit;
		List<CartRecoveryTransition> page = hasMore ? results.subList(0, limit) : results;
		String nextCursor = hasMore ? encodeCursor(page.get(page.size() - 1).getId()) : null;
		return new RecoveryAnalyticsResponseDto(
				page.stream().map(RecoveryAnalyticsItemDto::from).toList(), nextCursor, hasMore);
	}

	private Instant parseInstant(String value, String parameter) {
		try {
			return Instant.parse(value);
		} catch (RuntimeException exception) {
			throw new CartValidationException(parameter + " must be an ISO-8601 instant");
		}
	}

	private Long decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			long decoded = Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
			if (decoded < 1) {
				throw new IllegalArgumentException("Cursor must be positive");
			}
			return decoded;
		} catch (RuntimeException exception) {
			throw new CartValidationException("Cursor is invalid");
		}
	}

	private String encodeCursor(long id) {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(Long.toString(id).getBytes(StandardCharsets.UTF_8));
	}
}