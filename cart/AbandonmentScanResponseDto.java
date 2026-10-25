package com.demo.retailedge.cart;

public record AbandonmentScanResponseDto(int batchesCommitted, int cartsExamined,
		int notificationsQueued, int cartsSuperseded, Long lastSuccessfulCursor,
		boolean cycleComplete) {
}