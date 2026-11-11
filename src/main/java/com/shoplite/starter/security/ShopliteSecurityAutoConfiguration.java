package com.shoplite.starter.security;

import com.shoplite.starter.erasure.ErasedSubjectFilter;
import com.shoplite.starter.erasure.ErasedSubjects;
import com.shoplite.starter.security.dpop.DPoPReplayCache;
import com.shoplite.starter.security.dpop.DPoPValidator;
import com.shoplite.starter.security.dpop.InMemoryDPoPReplayCache;
import com.shoplite.starter.security.dpop.RedisDPoPReplayCache;
import com.shoplite.starter.security.dpop.ShopliteDPoPFilter;
import com.shoplite.starter.security.dpop.ShopliteDPoPProperties;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Auto-configures ShopLite's stateless JWT resource server (ADR 0001, ADR 0002, ADR 0003, ADR 0004):
 * <ul>
 * <li>Customer-only mode when only {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} is set.</li>
 * <li>Staff-only mode when only {@code shoplite.staff-oidc.issuer-uri} is set.</li>
 * <li>Dual-realm mode (routed by {@code iss}) when both are set.</li>
 * <li>Automatically attaches {@link SubjectCaptureFilter} (for structured access logs) and, when an
 * {@link ErasedSubjects} bean exists, {@link ErasedSubjectFilter} (ADR 0002 tombstone check).</li>
 * <li>Attaches {@link ShopliteDPoPFilter} for RFC 9449 sender-constrained token & replay protection (ADR 0004).</li>
 * </ul>
 */
@AutoConfiguration(
		after = com.shoplite.starter.erasure.ShopliteErasureAutoConfiguration.class,
		afterName = {
				"org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration",
				"org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration" },
		beforeName = {
				"org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
				"org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
				"org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration" })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ SecurityFilterChain.class, JwtAuthenticationToken.class })
@ConditionalOnProperty(name = "shoplite.security.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({ ShopliteSecurityProperties.class, ShopliteDPoPProperties.class })
@EnableMethodSecurity
public class ShopliteSecurityAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
	static class RedisDPoPReplayCacheConfiguration {

		@Bean
		@ConditionalOnMissingBean(DPoPReplayCache.class)
		@ConditionalOnBean(type = "org.springframework.data.redis.core.StringRedisTemplate")
		public DPoPReplayCache redisDPoPReplayCache(ObjectProvider<StringRedisTemplate> redisProvider) {
			StringRedisTemplate redis = redisProvider.getIfAvailable();
			if (redis != null) {
				return new RedisDPoPReplayCache(redis);
			}
			return new InMemoryDPoPReplayCache();
		}
	}

	@Bean
	@ConditionalOnMissingBean(DPoPReplayCache.class)
	public DPoPReplayCache inMemoryDPoPReplayCache() {
		return new InMemoryDPoPReplayCache();
	}

	@Bean
	@ConditionalOnMissingBean(DPoPValidator.class)
	public DPoPValidator shopliteDPoPValidator(DPoPReplayCache replayCache, ShopliteDPoPProperties dpopProperties) {
		return new DPoPValidator(replayCache, dpopProperties.getClockSkew());
	}

	@Bean
	@ConditionalOnMissingBean(SecurityFilterChain.class)
	public SecurityFilterChain shopliteSecurityFilterChain(HttpSecurity http,
			ShopliteSecurityProperties properties,
			ShopliteDPoPProperties dpopProperties,
			ObjectProvider<JwtDecoder> decoderProvider,
			ObjectProvider<ShopliteAuthorizeRequestsCustomizer> authorizeCustomizers,
			ObjectProvider<ErasedSubjects> erasedSubjectsProvider,
			ObjectProvider<DPoPValidator> dpopValidatorProvider,
			ObjectProvider<ObjectMapper> jsonProvider,
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:${shoplite.security.customer-issuer-uri:${OIDC_ISSUER_URI:}}}") String customerIssuer,
			@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:${shoplite.security.customer-jwk-set-uri:${SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI:}}}") String customerJwkSetUri,
			@Value("${shoplite.staff-oidc.issuer-uri:${shoplite.security.staff-issuer-uri:${STAFF_OIDC_ISSUER_URI:}}}") String staffIssuer,
			@Value("${shoplite.staff-oidc.jwk-set-uri:${shoplite.security.staff-jwk-set-uri:${STAFF_OIDC_JWK_SET_URI:}}}") String staffJwkSetUri) throws Exception {

		final String effectiveCustomerIssuer = StringUtils.hasText(customerIssuer) ? customerIssuer : properties.getCustomerIssuerUri();
		final String effectiveCustomerJwkSetUri = StringUtils.hasText(customerJwkSetUri) ? customerJwkSetUri : properties.getCustomerJwkSetUri();
		final String effectiveStaffIssuer = StringUtils.hasText(staffIssuer) ? staffIssuer : properties.getStaffIssuerUri();
		final String effectiveStaffJwkSetUri = StringUtils.hasText(staffJwkSetUri) ? staffJwkSetUri : properties.getStaffJwkSetUri();

		List<String> audiences = properties.getAudiences();
		boolean hasCustomer = StringUtils.hasText(effectiveCustomerIssuer);
		boolean hasStaff = StringUtils.hasText(effectiveStaffIssuer);

		http.authorizeHttpRequests(auth -> {
			auth.requestMatchers("/actuator/**").permitAll();
			authorizeCustomizers.orderedStream().forEach(c -> c.customize(auth));
			auth.anyRequest().authenticated();
		});

		if (hasCustomer && hasStaff) {
			JwtDecoder customerTokens = resolveCustomerDecoder(decoderProvider, effectiveCustomerIssuer,
					effectiveCustomerJwkSetUri, audiences);
			JwtDecoder staffTokens = ShopliteJwtSupport.decoder(effectiveStaffIssuer, effectiveStaffJwkSetUri, audiences);
			http.oauth2ResourceServer(rs -> rs.authenticationManagerResolver(
					ShopliteJwtSupport.byIssuer(effectiveCustomerIssuer, customerTokens, effectiveStaffIssuer, staffTokens,
							properties.getStaffPrincipalClaim())));
		}
		else if (hasStaff) {
			JwtDecoder staffTokens = decoderProvider.getIfAvailable(
					() -> ShopliteJwtSupport.decoder(effectiveStaffIssuer, effectiveStaffJwkSetUri, audiences));
			http.oauth2ResourceServer(rs -> rs.jwt(
					jwt -> jwt.decoder(staffTokens)
					.jwtAuthenticationConverter(ShopliteJwtSupport.staffConverter(properties.getStaffPrincipalClaim()))));
		}
		else {
			http.oauth2ResourceServer(rs -> rs.jwt(jwt -> {
				if (!audiences.isEmpty() && hasCustomer) {
					jwt.decoder(ShopliteJwtSupport.decoder(effectiveCustomerIssuer, effectiveCustomerJwkSetUri, audiences));
				}
				jwt.jwtAuthenticationConverter(ShopliteJwtSupport.customerConverter());
			}));
		}

		http.addFilterAfter(new SubjectCaptureFilter(), BearerTokenAuthenticationFilter.class);
		ErasedSubjects erasedSubjects = erasedSubjectsProvider.getIfAvailable();
		if (erasedSubjects != null) {
			ObjectMapper json = jsonProvider.getIfAvailable(ObjectMapper::new);
			http.addFilterAfter(new ErasedSubjectFilter(erasedSubjects, json), BearerTokenAuthenticationFilter.class);
		}

		if (dpopProperties.isEnabled()) {
			DPoPValidator dpopValidator = dpopValidatorProvider.getIfAvailable(
					() -> new DPoPValidator(new InMemoryDPoPReplayCache(), dpopProperties.getClockSkew()));
			ObjectMapper json = jsonProvider.getIfAvailable(ObjectMapper::new);
			http.addFilterAfter(new ShopliteDPoPFilter(dpopValidator, dpopProperties, json), BearerTokenAuthenticationFilter.class);
		}

		http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.csrf(csrf -> csrf.disable());
		return http.build();
	}

	private static JwtDecoder resolveCustomerDecoder(ObjectProvider<JwtDecoder> decoderProvider,
			String customerIssuer, String customerJwkSetUri, List<String> audiences) {
		if (!audiences.isEmpty()) {
			return ShopliteJwtSupport.decoder(customerIssuer, customerJwkSetUri, audiences);
		}
		return decoderProvider.getIfAvailable(
				() -> ShopliteJwtSupport.decoder(customerIssuer, customerJwkSetUri, audiences));
	}

}
