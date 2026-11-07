package com.shoplite.starter.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shoplite.starter.outbox.OutboxStore;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

class AuditEventTests {

	@AfterEach
	void cleanup() {
		SecurityContextHolder.clearContext();
		MDC.clear();
	}

	@Test
	void rejectsPersonalDataKeysInDetails() {
		assertThatThrownBy(() -> new AuditEvent("sub-1", "shoplite-internal", "customer.disable", "customer",
				"cust-1", "SUCCESS", Instant.now(), Map.of("email", "alice@example.com")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("email");
	}

	@Test
	void structuredLogAuditRecorderEmitsKeyValuePairs() {
		Logger logger = (Logger) LoggerFactory.getLogger(StructuredLogAuditRecorder.LOGGER_NAME);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			AuditRecorder recorder = new StructuredLogAuditRecorder();
			recorder.record(new AuditEvent("staff-sub-1", "shoplite-internal", "product.update", "product", "42",
					"SUCCESS", Instant.parse("2026-11-07T10:00:00Z"), Map.of("version", "3")));

			assertThat(appender.list).hasSize(1);
			Map<String, Object> kvs = appender.list.getFirst()
				.getKeyValuePairs()
				.stream()
				.collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
			assertThat(kvs).containsEntry("actor_sub", "staff-sub-1")
				.containsEntry("actor_realm", "shoplite-internal")
				.containsEntry("action", "product.update")
				.containsEntry("target_type", "product")
				.containsEntry("target_id", "42")
				.containsEntry("outcome", "SUCCESS")
				.containsEntry("version", "3");
			assertThat(kvs.get("event_id")).isNotNull();
			assertThat(kvs.get("event_type")).isEqualTo("audit.recorded");
		}
		finally {
			logger.detachAppender(appender);
		}
	}

	@Test
	void auditContextExtractsIdentityAndTraceContext() {
		Jwt jwt = Jwt.withTokenValue("mock-token")
			.header("alg", "none")
			.subject("staff-123")
			.issuer("https://auth.neststack.site/realms/shoplite-internal")
			.build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
		MDC.put("traceId", "trace-abc-456");

		AuditEvent event = AuditContext.buildEvent("catalog.update", "product", "99", "SUCCESS", Map.of("price", "500000"));

		assertThat(event.actorSub()).isEqualTo("staff-123");
		assertThat(event.actorRealm()).isEqualTo("shoplite-internal");
		assertThat(event.action()).isEqualTo("catalog.update");
		assertThat(event.targetType()).isEqualTo("product");
		assertThat(event.targetId()).isEqualTo("99");
		assertThat(event.traceId()).isEqualTo("trace-abc-456");
		assertThat(event.outcome()).isEqualTo("SUCCESS");
		assertThat(event.details()).containsEntry("price", "500000");
	}

	@Test
	void compositeOutboxAuditRecorderEnqueuesToStore() {
		AuditRecorder delegate = mock(AuditRecorder.class);
		OutboxStore store = mock(OutboxStore.class);
		ObjectMapper json = new ObjectMapper();

		CompositeOutboxAuditRecorder recorder = new CompositeOutboxAuditRecorder(delegate, store, json);
		AuditEvent event = new AuditEvent("staff-1", "shoplite-internal", "order.cancel", "order", "1001", "SUCCESS",
				Instant.now(), Map.of());

		recorder.record(event);

		verify(delegate).record(event);
		verify(store).enqueue(eq(event.eventId()), eq("1001"), eq("audit.recorded"), any());
	}

	@Test
	void autoConfigurationDefaultsToLogRecorder() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ShopliteAuditAutoConfiguration.class))
			.run(context -> {
				assertThat(context).hasSingleBean(AuditRecorder.class);
				assertThat(context.getBean(AuditRecorder.class)).isInstanceOf(StructuredLogAuditRecorder.class);
			});
	}

	@Test
	void autoConfigurationUpgradesToCompositeWhenOutboxEnabled() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ShopliteAuditAutoConfiguration.class))
			.withUserConfiguration(OutboxTestConfig.class)
			.withPropertyValues("shoplite.audit.outbox-enabled=true")
			.run(context -> {
				assertThat(context).hasSingleBean(AuditRecorder.class);
				assertThat(context.getBean(AuditRecorder.class)).isInstanceOf(CompositeOutboxAuditRecorder.class);
			});
	}

	@Configuration
	static class OutboxTestConfig {
		@Bean
		OutboxStore outboxStore() {
			return mock(OutboxStore.class);
		}

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}
	}

}
