package com.demo.retailedge.cart;

/** Checkout lifecycle, independent of cart recovery lifecycle. */
public enum CheckoutStatus {
	PENDING,
	COMPLETED,
	CANCELLED
}