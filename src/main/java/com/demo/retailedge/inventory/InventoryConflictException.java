package com.demo.retailedge.inventory;

public class InventoryConflictException extends RuntimeException {

	/** Creates a conflict representing duplicate inventory or a stale version. */
	public InventoryConflictException(String message) {
		super(message);
	}
}