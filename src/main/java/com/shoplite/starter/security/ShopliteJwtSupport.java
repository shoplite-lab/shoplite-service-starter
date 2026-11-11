package com.shoplite.starter.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.util.StringUtils;

/**
 * Shared JWT resource-server building blocks for ShopLite services (ADR 0001, ADR 0003).
 *
 * <p>Two Keycloak realms issue tokens in ShopLite:
 * <ul>
 * <li>{@code shoplite} (customers and machine service accounts): {@code realm_access.roles}
 * become {@code ROLE_*} authorities.</li>
 * <li>{@code shoplite-internal} (staff, mandatory OTP): {@code groups} claim entries become
 * {@code STAFF_*} authorities.</li>
 * </ul>
 * Keeping distinct prefixes guarantees a customer-realm role can never satisfy a staff check.
 */
public final class ShopliteJwtSupport {

	public static final String CUSTOMER_ROLE_PREFIX = "ROLE_";

	public static final String STAFF_GROUP_PREFIX = "STAFF_";

	private ShopliteJwtSupport() {
	}

	/**
	 * Adapts DPoP-bound tokens presented over the Bearer scheme: Spring Security 7's
	 * {@code BearerTokenAuthenticationFilter} strictly rejects tokens containing {@code cnf.jkt}.
	 * We preserve the binding under {@code dpop_cnf} so {@link com.shoplite.starter.security.dpop.ShopliteDPoPFilter}
	 * can enforce the RFC 9449 proof check without Spring Security short-circuiting.
	 */
	public static Jwt adaptDPoPJwt(Jwt jwt) {
		if (jwt != null && jwt.getClaims().containsKey("cnf")) {
			Map<String, Object> claims = new LinkedHashMap<>(jwt.getClaims());
			Object cnf = claims.remove("cnf");
			claims.put("dpop_cnf", cnf);
			return new Jwt(jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getHeaders(), claims);
		}
		return jwt;
	}

	/**
	 * Customer realm converter: maps {@code realm_access.roles} to {@code ROLE_*} and keeps
	 * {@code preferred_username} as the principal name for backward compatibility with existing
	 * controllers; the opaque {@code sub} is always read via {@link Jwt#getSubject()}.
	 */
	public static Converter<Jwt, ? extends AbstractAuthenticationToken> customerConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setPrincipalClaimName("preferred_username");
		converter.setJwtGrantedAuthoritiesConverter(ShopliteJwtSupport::realmRoles);
		return jwt -> converter.convert(adaptDPoPJwt(jwt));
	}

	/**
	 * Staff realm converter: maps {@code groups} to {@code STAFF_*} and sets the principal name
	 * to the opaque {@code sub} so logs and audit records never carry a username or email.
	 */
	public static Converter<Jwt, ? extends AbstractAuthenticationToken> staffConverter() {
		return staffConverter(JwtClaimNames.SUB);
	}

	/**
	 * Same as {@link #staffConverter()} with the principal claim chosen by the service. A service
	 * that logged {@code preferred_username} before adopting the starter can keep doing so until it
	 * decides to move to the opaque {@code sub}.
	 */
	public static Converter<Jwt, ? extends AbstractAuthenticationToken> staffConverter(String principalClaim) {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setPrincipalClaimName(principalClaim);
		converter.setJwtGrantedAuthoritiesConverter(ShopliteJwtSupport::staffGroups);
		return jwt -> converter.convert(adaptDPoPJwt(jwt));
	}

	/** Customer realm: {@code realm_access.roles} become {@code ROLE_*}. */
	@SuppressWarnings("unchecked")
	public static Collection<GrantedAuthority> realmRoles(Jwt jwt) {
		Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
		if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) {
			return List.of();
		}
		return ((List<String>) roles).stream()
			.map(role -> (GrantedAuthority) new SimpleGrantedAuthority(CUSTOMER_ROLE_PREFIX + role))
			.toList();
	}

	/** Staff realm: {@code groups} claim entries become {@code STAFF_*}. */
	public static Collection<GrantedAuthority> staffGroups(Jwt jwt) {
		List<String> groups = jwt.getClaimAsStringList("groups");
		if (groups == null) {
			return List.of();
		}
		return groups.stream()
			.map(group -> (GrantedAuthority) new SimpleGrantedAuthority(STAFF_GROUP_PREFIX + group))
			.toList();
	}

	/**
	 * Builds a token validator checking expiry, {@code iss}, and optionally that the token's
	 * {@code aud} claim contains at least one of {@code requiredAudiences} (ADR 0001 A3).
	 */
	public static OAuth2TokenValidator<Jwt> validator(String issuer, List<String> requiredAudiences) {
		OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
		if (requiredAudiences == null || requiredAudiences.isEmpty()) {
			return withIssuer;
		}
		OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
				aud -> aud != null && aud.stream().anyMatch(requiredAudiences::contains));
		return new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience);
	}

	/** Builds a lazy JWKS-backed decoder for a realm, checking issuer and optional audiences. */
	public static JwtDecoder decoder(String issuer, String jwkSetUri, List<String> requiredAudiences) {
		String keys = StringUtils.hasText(jwkSetUri) ? jwkSetUri : issuer + "/protocol/openid-connect/certs";
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(keys).build();
		decoder.setJwtValidator(validator(issuer, requiredAudiences));
		return decoder;
	}

	/** Dual-realm resolver: picks the customer or staff authentication manager from the token's {@code iss}. */
	public static AuthenticationManagerResolver<HttpServletRequest> byIssuer(String customerIssuer,
			JwtDecoder customerTokens, String staffIssuer, JwtDecoder staffTokens) {
		return byIssuer(customerIssuer, customerTokens, staffIssuer, staffTokens, JwtClaimNames.SUB);
	}

	public static AuthenticationManagerResolver<HttpServletRequest> byIssuer(String customerIssuer,
			JwtDecoder customerTokens, String staffIssuer, JwtDecoder staffTokens, String staffPrincipalClaim) {
		Map<String, AuthenticationManager> managers = Map.of(
				customerIssuer, manager(customerTokens, customerConverter()),
				staffIssuer, manager(staffTokens, staffConverter(staffPrincipalClaim)));
		return new JwtIssuerAuthenticationManagerResolver(managers::get);
	}

	private static AuthenticationManager manager(JwtDecoder decoder,
			Converter<Jwt, ? extends AbstractAuthenticationToken> converter) {
		JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
		provider.setJwtAuthenticationConverter(converter);
		return provider::authenticate;
	}

}
