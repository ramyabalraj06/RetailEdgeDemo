package com.demo.retailedge.cart;

import java.util.List;

public record RecoveryAnalyticsResponseDto(List<RecoveryAnalyticsItemDto> items,
		String nextCursor, boolean hasMore) {
}