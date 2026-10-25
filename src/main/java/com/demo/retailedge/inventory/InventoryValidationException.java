package com.demo.retailedge.inventory;

public class InventoryValidationException extends RuntimeException {

	/** Creates a safe validation error for inventory input. */
	public InventoryValidationException(String message) {
		super(message);
	}
}