package com.shoplite.starter.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration for OpenAPI 3.1 documentation via springdoc-openapi.
 * Configures service info and standardized security schemes for Bearer JWT and DPoP (RFC 9449).
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "shoplite.openapi.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ShopliteOpenApiProperties.class)
public class ShopliteOpenApiAutoConfiguration {

	public static final String SECURITY_SCHEME_BEARER = "bearer-jwt";
	public static final String SECURITY_SCHEME_DPOP = "dpop";

	@Bean
	@ConditionalOnMissingBean(OpenAPI.class)
	public OpenAPI shopliteOpenAPI(ShopliteOpenApiProperties properties, Environment env) {
		String appName = env.getProperty("spring.application.name", "shoplite-service");
		String title = properties.getTitle() != null && !properties.getTitle().isBlank()
				? properties.getTitle()
				: appName + " API";

		Info info = new Info()
				.title(title)
				.description(properties.getDescription())
				.version(properties.getVersion())
				.contact(new Contact().name(properties.getContactName()).url(properties.getContactUrl()));

		SecurityScheme bearerScheme = new SecurityScheme()
				.type(SecurityScheme.Type.HTTP)
				.scheme("bearer")
				.bearerFormat("JWT")
				.description("RSA RS256 Bearer JWT token issued by Keycloak");

		SecurityScheme dpopScheme = new SecurityScheme()
				.type(SecurityScheme.Type.APIKEY)
				.in(SecurityScheme.In.HEADER)
				.name("DPoP")
				.description("RFC 9449 DPoP proof token (ES256 ECDSA P-256)");

		Components components = new Components()
				.addSecuritySchemes(SECURITY_SCHEME_BEARER, bearerScheme)
				.addSecuritySchemes(SECURITY_SCHEME_DPOP, dpopScheme);

		return new OpenAPI()
				.info(info)
				.components(components)
				.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_BEARER));
	}

}
