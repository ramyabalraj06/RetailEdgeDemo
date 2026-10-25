package com.demo.retailedge.inventory;

public class StoreAccessDeniedException extends RuntimeException {

	/** Creates a store ownership denial without identifying the requested store. */
	public StoreAccessDeniedException() {
		super("The authenticated user cannot access this store");
	}
}