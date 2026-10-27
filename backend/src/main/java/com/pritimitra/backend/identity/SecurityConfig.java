package com.pritimitra.backend.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless OAuth 2.0 resource server. Product APIs are default-deny: any new {@code /api/v1/**} route
 * requires an ACTIVE, onboarded account unless it is explicitly listed here.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

	@Bean
	SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, EligibleUserAuthorizationManager eligibleUser)
			throws Exception {
		http
				// Bearer-token API with no cookies, so there is no ambient credential for CSRF to abuse.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/api/v1/moderation/**").authenticated()
						.requestMatchers("/api/v1/me", "/api/v1/onboarding/**").authenticated()
						.requestMatchers("/api/v1/**").access(eligibleUser)
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
		return http.build();
	}
}
