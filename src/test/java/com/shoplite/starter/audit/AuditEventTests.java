package com.shoplite.starter.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;

class AuditEventTests {

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
		}
		finally {
			logger.detachAppender(appender);
		}
	}

}
