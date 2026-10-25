package com.demo.retailedge.cart;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class CartRecoveryTestConfiguration {

	@Bean
	@Primary
	MutableTestClock mutableTestClock() {
		return new MutableTestClock(Instant.parse("2026-10-25T12:00:00Z"));
	}
}