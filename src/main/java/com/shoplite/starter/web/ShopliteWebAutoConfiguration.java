package com.shoplite.starter.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Auto-configures {@link AccessLogFilter} for servlet web applications (ADR 0003).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ OncePerRequestFilter.class, HandlerMapping.class })
@ConditionalOnProperty(name = "shoplite.web.access-log.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ShopliteWebProperties.class)
public class ShopliteWebAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(AccessLogFilter.class)
	public AccessLogFilter shopliteAccessLogFilter(ShopliteWebProperties properties) {
		return new AccessLogFilter(properties.isIgnoreActuator());
	}

	@Bean
	@ConditionalOnMissingBean(ShopliteProblemDetailsExceptionHandler.class)
	@ConditionalOnProperty(name = "shoplite.web.problem-details.enabled", havingValue = "true", matchIfMissing = true)
	public ShopliteProblemDetailsExceptionHandler shopliteProblemDetailsExceptionHandler() {
		return new ShopliteProblemDetailsExceptionHandler();
	}

}
