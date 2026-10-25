package com.demo.retailedge.cart;

public record CartScanBatchResult(int cartsExamined, int notificationsQueued,
		int cartsSuperseded, Long lastSuccessfulCursor, boolean cycleComplete) {
}