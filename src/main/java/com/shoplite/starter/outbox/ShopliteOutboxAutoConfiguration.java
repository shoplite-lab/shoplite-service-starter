package com.shoplite.starter.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configures {@link OutboxStore}, {@link OutboxMetrics}, {@link OutboxRelay} and
 * {@link OutboxMaintenance} when {@code shoplite.outbox.enabled=true} and an
 * {@link OutboxPublisher} bean is registered (ADR 0003).
 */
@AutoConfiguration(afterName = {
		"org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
		"org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration" })
@ConditionalOnClass({ JdbcTemplate.class, MeterRegistry.class })
@ConditionalOnProperty(name = "shoplite.outbox.enabled", havingValue = "true")
@EnableConfigurationProperties(OutboxProperties.class)
public class ShopliteOutboxAutoConfiguration {

	@Bean
	@ConditionalOnBean(JdbcTemplate.class)
	@ConditionalOnMissingBean(OutboxStore.class)
	public OutboxStore outboxStore(JdbcTemplate jdbc) {
		return new OutboxStore(jdbc);
	}

	@Bean
	@ConditionalOnBean({ OutboxStore.class, MeterRegistry.class })
	@ConditionalOnMissingBean(OutboxMetrics.class)
	public OutboxMetrics outboxMetrics(MeterRegistry registry, OutboxStore store) {
		return new OutboxMetrics(registry, store);
	}

	@Bean
	@ConditionalOnBean({ OutboxStore.class, OutboxPublisher.class, OutboxMetrics.class })
	@ConditionalOnMissingBean(OutboxRelay.class)
	public OutboxRelay outboxRelay(OutboxProperties properties, OutboxStore store, OutboxPublisher publisher,
			OutboxMetrics metrics) {
		return new OutboxRelay(properties.isEnabled(), properties.getBatchSize(), properties.getMaxAttempts(), store,
				publisher, metrics);
	}

	@Bean
	@ConditionalOnBean(OutboxStore.class)
	@ConditionalOnMissingBean(OutboxMaintenance.class)
	public OutboxMaintenance outboxMaintenance(OutboxProperties properties, OutboxStore store) {
		return new OutboxMaintenance(properties.isEnabled(), properties.getStaleAfter(), properties.getRetention(),
				properties.getMaxAttempts(), store);
	}

}
