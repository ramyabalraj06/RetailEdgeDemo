package com.demo.retailedge.inventory;

public class InventoryNotFoundException extends RuntimeException {

	/** Creates a not-found error without exposing cross-store record details. */
	public InventoryNotFoundException(String message) {
		super(message);
	}
}