package com.demo.retailedge.cart;

public class CartNotFoundException extends RuntimeException {

	public CartNotFoundException() {
		super("Cart recovery not found");
	}
}