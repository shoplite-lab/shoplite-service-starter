package com.shoplite.starter.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ShopliteOpenApiAutoConfigurationTests {

	private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ShopliteOpenApiAutoConfiguration.class));

	@Test
	void registersOpenApiBeanWithDefaultPropertiesAndSecuritySchemes() {
		runner.withPropertyValues("spring.application.name=shoplite-order-service")
				.run(context -> {
					assertThat(context).hasSingleBean(OpenAPI.class);
					OpenAPI openAPI = context.getBean(OpenAPI.class);

					assertThat(openAPI.getInfo().getTitle()).isEqualTo("shoplite-order-service API");
					assertThat(openAPI.getComponents().getSecuritySchemes())
							.containsKey(ShopliteOpenApiAutoConfiguration.SECURITY_SCHEME_BEARER)
							.containsKey(ShopliteOpenApiAutoConfiguration.SECURITY_SCHEME_DPOP);
				});
	}

	@Test
	void respectsCustomTitleAndDisabledProperty() {
		runner.withPropertyValues(
				"shoplite.openapi.title=Custom Shop Title",
				"shoplite.openapi.version=v2.5"
		).run(context -> {
			assertThat(context).hasSingleBean(OpenAPI.class);
			OpenAPI openAPI = context.getBean(OpenAPI.class);
			assertThat(openAPI.getInfo().getTitle()).isEqualTo("Custom Shop Title");
			assertThat(openAPI.getInfo().getVersion()).isEqualTo("v2.5");
		});

		runner.withPropertyValues("shoplite.openapi.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(OpenAPI.class));
	}

}
