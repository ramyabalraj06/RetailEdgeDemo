package com.demo.retailedge.cart;

/** Lifecycle of a cart recovery attempt. */
public enum RecoveryStatus {
	PENDING,
	NOTIFIED,
	CONVERTED,
	EXPIRED
}