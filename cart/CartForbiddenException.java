package com.demo.retailedge.cart;

public class CartForbiddenException extends RuntimeException {

	public CartForbiddenException() {
		super("Access to this cart is forbidden");
	}
}