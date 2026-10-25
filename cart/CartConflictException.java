package com.demo.retailedge.cart;

public class CartConflictException extends RuntimeException {

	public CartConflictException(String message) {
		super(message);
	}
}