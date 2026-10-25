package com.demo.retailedge.inventory;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a validated request ID to logging context and response headers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InventoryRequestCorrelationFilter extends OncePerRequestFilter {

	private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String suppliedId = request.getHeader("X-Request-Id");
		String requestId = suppliedId != null && SAFE_REQUEST_ID.matcher(suppliedId).matches()
				? suppliedId : UUID.randomUUID().toString();
		response.setHeader("X-Request-Id", requestId);
		try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
			chain.doFilter(request, response);
		}
	}
}