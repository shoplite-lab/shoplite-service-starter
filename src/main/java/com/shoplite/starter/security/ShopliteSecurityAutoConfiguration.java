package com.shoplite.starter.security;

import com.shoplite.starter.erasure.ErasedSubjectFilter;
import com.shoplite.starter.erasure.ErasedSubjects;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
 * Auto-configures ShopLite's stateless JWT resource server (ADR 0001, ADR 0002, ADR 0003):
 * <ul>
 * <li>Customer-only mode when only {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} is set.</li>
 * <li>Staff-only mode when only {@code shoplite.staff-oidc.issuer-uri} is set.</li>
 * <li>Dual-realm mode (routed by {@code iss}) when both are set.</li>
 * <li>Automatically attaches {@link SubjectCaptureFilter} (for structured access logs) and, when an
 * {@link ErasedSubjects} bean exists, {@link ErasedSubjectFilter} (ADR 0002 tombstone check).</li>
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
@EnableConfigurationProperties(ShopliteSecurityProperties.class)
@EnableMethodSecurity
public class ShopliteSecurityAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(SecurityFilterChain.class)
	public SecurityFilterChain shopliteSecurityFilterChain(HttpSecurity http,
			ShopliteSecurityProperties properties,
			ObjectProvider<JwtDecoder> decoderProvider,
			ObjectProvider<ShopliteAuthorizeRequestsCustomizer> authorizeCustomizers,
			ObjectProvider<ErasedSubjects> erasedSubjectsProvider,
			ObjectProvider<ObjectMapper> jsonProvider,
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String customerIssuer,
			@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String customerJwkSetUri,
			@Value("${shoplite.staff-oidc.issuer-uri:}") String staffIssuer,
			@Value("${shoplite.staff-oidc.jwk-set-uri:}") String staffJwkSetUri) throws Exception {

		List<String> audiences = properties.getAudiences();
		boolean hasCustomer = StringUtils.hasText(customerIssuer);
		boolean hasStaff = StringUtils.hasText(staffIssuer);

		http.authorizeHttpRequests(auth -> {
			auth.requestMatchers("/actuator/**").permitAll();
			authorizeCustomizers.orderedStream().forEach(c -> c.customize(auth));
			auth.anyRequest().authenticated();
		});

		if (hasCustomer && hasStaff) {
			JwtDecoder customerTokens = resolveCustomerDecoder(decoderProvider, customerIssuer,
					customerJwkSetUri, audiences);
			JwtDecoder staffTokens = ShopliteJwtSupport.decoder(staffIssuer, staffJwkSetUri, audiences);
			http.oauth2ResourceServer(rs -> rs.authenticationManagerResolver(
					ShopliteJwtSupport.byIssuer(customerIssuer, customerTokens, staffIssuer, staffTokens,
							properties.getStaffPrincipalClaim())));
		}
		else if (hasStaff) {
			JwtDecoder staffTokens = decoderProvider.getIfAvailable(
					() -> ShopliteJwtSupport.decoder(staffIssuer, staffJwkSetUri, audiences));
			http.oauth2ResourceServer(rs -> rs.jwt(
					jwt -> jwt.decoder(staffTokens)
					.jwtAuthenticationConverter(ShopliteJwtSupport.staffConverter(properties.getStaffPrincipalClaim()))));
		}
		else {
			http.oauth2ResourceServer(rs -> rs.jwt(jwt -> {
				if (!audiences.isEmpty() && hasCustomer) {
					jwt.decoder(ShopliteJwtSupport.decoder(customerIssuer, customerJwkSetUri, audiences));
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
