package com.demo.retailedge.inventory;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/** Stateless JWT security for the inventory API. */
@Configuration
@EnableWebSecurity
public class InventorySecurityConfiguration {

	/** Builds an RS256-only decoder with required issuer, audience, and inventory claims. */
	@Bean
	public JwtDecoder inventoryJwtDecoder(
			@Value("${security.jwt.issuer-uri}") String issuerUri,
			@Value("${security.jwt.jwks-uri}") String jwksUri,
			@Value("${security.jwt.audience}") String audience) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(issuerUri),
				new InventoryJwtClaimsValidator(audience, Clock.systemUTC())));
		return decoder;
	}

	/** Configures stateless authentication and ADMIN/service-only inventory access. */
	@Bean
	public SecurityFilterChain inventorySecurityFilterChain(
			HttpSecurity http,
			JwtAuthenticationConverter jwtAuthenticationConverter,
			AuthenticationEntryPoint inventoryAuthenticationEntryPoint,
			AccessDeniedHandler inventoryAccessDeniedHandler) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptions -> exceptions
					.authenticationEntryPoint(inventoryAuthenticationEntryPoint)
					.accessDeniedHandler(inventoryAccessDeniedHandler))
			.authorizeHttpRequests(authorize -> authorize
					.requestMatchers(HttpMethod.OPTIONS, "/**").denyAll()
					.requestMatchers("/inventory-ui", "/css/**", "/js/**").permitAll()
					.requestMatchers("/api/**").access(inventoryAuthorizationManager())
					.anyRequest().denyAll())
			.oauth2ResourceServer(resourceServer -> resourceServer
					.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
					.authenticationEntryPoint(inventoryAuthenticationEntryPoint));
		return http.build();
	}

	/** Converts role and scope claims into authorities used by the inventory policy. */
	@Bean
	public JwtAuthenticationConverter inventoryJwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(this::authorities);
		return converter;
	}

	/** Writes a safe JSON 401 and distinguishes missing credentials from invalid tokens. */
	@Bean
	public AuthenticationEntryPoint inventoryAuthenticationEntryPoint(ObjectMapper objectMapper) {
		return (request, response, exception) -> {
			String authorization = request.getHeader("Authorization");
			String code = authorization == null ? "AUTHENTICATION_REQUIRED" : tokenErrorCode(exception);
			writeError(objectMapper, response, HttpServletResponse.SC_UNAUTHORIZED, code,
					"Authentication is required");
		};
	}

	/** Writes a safe JSON 403 for authenticated users without inventory authority. */
	@Bean
	public AccessDeniedHandler inventoryAccessDeniedHandler(ObjectMapper objectMapper) {
		return (request, response, exception) -> writeError(objectMapper, response,
				HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN_INVENTORY_ROLE",
				"The authenticated user cannot perform this inventory operation");
	}

	private AuthorizationManager<RequestAuthorizationContext> inventoryAuthorizationManager() {
		return (authentication, context) -> {
			Authentication current = authentication.get();
			boolean authenticated = current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken);
			Set<String> authorities = new LinkedHashSet<>();
			current.getAuthorities().forEach(authority -> authorities.add(authority.getAuthority()));
			boolean permittedRole = authorities.contains("ROLE_ADMIN")
					|| authorities.contains("ROLE_INVENTORY_SERVICE");
			return new AuthorizationDecision(authenticated && permittedRole
					&& authorities.contains("SCOPE_inventory:manage"));
		};
	}

	private Collection<GrantedAuthority> authorities(Jwt jwt) {
		Set<GrantedAuthority> authorities = new LinkedHashSet<>();
		addAuthorities(authorities, jwt.getClaim("role"), "ROLE_");
		addAuthorities(authorities, jwt.getClaim("scope"), "SCOPE_");
		return authorities;
	}

	private void addAuthorities(Set<GrantedAuthority> authorities, Object claim, String prefix) {
		if (claim instanceof String values) {
			for (String value : values.split("[,\\s]+")) {
				addAuthority(authorities, prefix, value);
			}
		} else if (claim instanceof Collection<?> values) {
			values.stream().filter(String.class::isInstance).map(String.class::cast)
					.forEach(value -> addAuthority(authorities, prefix, value));
		}
	}

	private void addAuthority(Set<GrantedAuthority> authorities, String prefix, String value) {
		String normalized = value.trim();
		if (!normalized.isEmpty()) {
			String authority = normalized.startsWith(prefix) ? normalized : prefix + normalized;
			authorities.add(new SimpleGrantedAuthority(authority));
		}
	}

	private String tokenErrorCode(Throwable exception) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof JwtValidationException validationException) {
				for (OAuth2Error error : validationException.getErrors()) {
					if (Set.of("AUTH_TOKEN_INVALID", "AUTH_TOKEN_EXPIRED", "AUTH_TOKEN_NOT_YET_VALID")
							.contains(error.getErrorCode())) {
						return error.getErrorCode();
					}
				}
			}
			current = current.getCause();
		}
		return "AUTH_TOKEN_INVALID";
	}

	private void writeError(ObjectMapper objectMapper, HttpServletResponse response, int status,
			String code, String message) throws java.io.IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), ApiErrorResponseDto.of(code, message));
	}
}