package com.shoplite.starter.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * Optional hook for a service to add route-level authorization rules (such as public read endpoints)
 * before the default {@code anyRequest().authenticated()} rule is applied, without replacing the
 * starter's entire {@code SecurityFilterChain}.
 */
@FunctionalInterface
public interface ShopliteAuthorizeRequestsCustomizer {

	void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth);

}
