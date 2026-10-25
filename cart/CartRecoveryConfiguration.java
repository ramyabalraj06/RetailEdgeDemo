package com.demo.retailedge.cart;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class CartRecoveryConfiguration {

	@Bean
	public Clock cartRecoveryClock() {
		return Clock.systemUTC();
	}
}