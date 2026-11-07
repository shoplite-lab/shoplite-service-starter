package com.shoplite.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

class ShopliteSecurityTests {

	private static final String CUSTOMERS = "http://auth.test/realms/shoplite";

	private static final String STAFF = "http://auth.test/realms/shoplite-internal";

	private final KeyPair keys = rsa();

	@Test
	void customerRolesBecomeRoleAuthorities() {
		NimbusJwtDecoder decoder = decoder(CUSTOMERS, List.of());
		Jwt jwt = decoder.decode(sign(CUSTOMERS, List.of(), Map.of("realm_access", Map.of("roles", List.of("customer", "stock-writer")))));

		Authentication auth = ShopliteJwtSupport.customerConverter().convert(jwt);

		assertThat(authoritiesWithPrefix(auth, "ROLE_")).containsExactlyInAnyOrder("ROLE_customer", "ROLE_stock-writer");
	}

	@Test
	void staffGroupsBecomeStaffAuthoritiesAndPrincipalIsOpaqueSub() {
		NimbusJwtDecoder decoder = decoder(STAFF, List.of());
		Jwt jwt = decoder.decode(sign(STAFF, List.of(), Map.of("preferred_username", "alice", "groups", List.of("support", "super-admin"))));

		Authentication auth = ShopliteJwtSupport.staffConverter().convert(jwt);

		assertThat(auth.getName()).isEqualTo("subject-1");
		assertThat(authoritiesWithPrefix(auth, "STAFF_")).containsExactlyInAnyOrder("STAFF_support", "STAFF_super-admin");
	}

	@Test
	void wrongIssuerIsRejected() {
		NimbusJwtDecoder decoder = decoder(STAFF, List.of());
		String customerToken = sign(CUSTOMERS, List.of(), Map.of("groups", List.of("super-admin")));

		assertThatThrownBy(() -> decoder.decode(customerToken)).isInstanceOf(BadJwtException.class);
	}

	@Test
	void audienceValidationAcceptsMatchingAudienceAndRejectsMissingOrOtherAudience() {
		NimbusJwtDecoder decoder = decoder(CUSTOMERS, List.of("order-service"));

		String valid = sign(CUSTOMERS, List.of("order-service", "account"), Map.of());
		assertThat(decoder.decode(valid).getSubject()).isEqualTo("subject-1");

		String wrongAud = sign(CUSTOMERS, List.of("product-service"), Map.of());
		assertThatThrownBy(() -> decoder.decode(wrongAud)).isInstanceOf(BadJwtException.class);

		String noAud = sign(CUSTOMERS, List.of(), Map.of());
		assertThatThrownBy(() -> decoder.decode(noAud)).isInstanceOf(BadJwtException.class);
	}

	@Test
	void autoConfigurationRegistersDefaultChainAndRespectsCustomBeanOrNonWebContext() {
		WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(
					WebMvcAutoConfiguration.class,
					DispatcherServletAutoConfiguration.class,
					ServletWebSecurityAutoConfiguration.class,
					OAuth2ResourceServerAutoConfiguration.class,
					ShopliteSecurityAutoConfiguration.class))
			.withPropertyValues("spring.security.oauth2.resourceserver.jwt.issuer-uri=" + CUSTOMERS);

		webRunner.run(ctx -> assertThat(ctx).hasSingleBean(SecurityFilterChain.class)
			.hasBean("shopliteSecurityFilterChain"));

		webRunner.withUserConfiguration(CustomSecurityConfig.class)
			.run(ctx -> assertThat(ctx).hasSingleBean(SecurityFilterChain.class)
				.doesNotHaveBean("shopliteSecurityFilterChain")
				.hasBean("customChain"));

		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ShopliteSecurityAutoConfiguration.class))
			.run(ctx -> assertThat(ctx).doesNotHaveBean(SecurityFilterChain.class));
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomSecurityConfig {

		@Bean
		SecurityFilterChain customChain(HttpSecurity http) throws Exception {
			return http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
		}

	}

	private NimbusJwtDecoder decoder(String issuer, List<String> audiences) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
		decoder.setJwtValidator(ShopliteJwtSupport.validator(issuer, audiences));
		return decoder;
	}

	private String sign(String issuer, List<String> audience, Map<String, Object> claims) {
		try {
			Instant now = Instant.now();
			JWTClaimsSet.Builder set = new JWTClaimsSet.Builder().issuer(issuer)
				.subject("subject-1")
				.issueTime(Date.from(now.minusSeconds(60)))
				.expirationTime(Date.from(now.plusSeconds(300)));
			if (!audience.isEmpty()) {
				set.audience(audience);
			}
			claims.forEach(set::claim);
			SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), set.build());
			jwt.sign(new RSASSASigner(keys.getPrivate()));
			return jwt.serialize();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static List<String> authoritiesWithPrefix(Authentication auth, String prefix) {
		return auth.getAuthorities().stream().map(Object::toString).filter(a -> a.startsWith(prefix)).toList();
	}

	private static KeyPair rsa() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
