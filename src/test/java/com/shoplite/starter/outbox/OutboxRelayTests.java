package com.shoplite.starter.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OutboxRelayTests.TestApp.class, properties = "shoplite.outbox.enabled=true")
class OutboxRelayTests {

	@Autowired
	OutboxStore store;

	@Autowired
	OutboxRelay relay;

	@Autowired
	OutboxMaintenance maintenance;

	@Autowired
	OutboxMetrics metrics;

	@Autowired
	SimpleMeterRegistry registry;

	@Autowired
	RecordingPublisher publisher;

	@BeforeEach
	void reset() {
		org.mockito.Mockito.reset(store);
		publisher.reset();
	}

	@Test
	void sendsDueRowsMarksThemSentAndIncrementsCounter() {
		when(store.claim(20, 100)).thenReturn(List.of(
				new OutboxStore.OutboxRow(1L, "order-1", "order.created", "{\"id\":1}", 1),
				new OutboxStore.OutboxRow(2L, "order-2", "order.created", "{\"id\":2}", 1)));

		OutboxRelay.RunResult result = relay.runOnce();

		assertThat(result).isEqualTo(new OutboxRelay.RunResult(2, 2, 0, 0, 0));
		verify(store).markSent(1L);
		verify(store).markSent(2L);
		assertThat(publisher.sentAggregates).containsExactly("order-1", "order-2");
		assertThat(registry.get("outbox.sent").counter().count()).isGreaterThanOrEqualTo(2.0);
	}

	@Test
	void stopsAtFirstFailureAndReleasesUntriedRowsWithoutConsumingAnAttempt() {
		when(store.claim(20, 100)).thenReturn(List.of(
				new OutboxStore.OutboxRow(10L, "order-1", "order.created", "{\"id\":1}", 1),
				new OutboxStore.OutboxRow(11L, "order-2", "order.created", "{\"id\":2}", 1),
				new OutboxStore.OutboxRow(12L, "order-3", "order.created", "{\"id\":3}", 1)));
		when(store.release(List.of(11L, 12L))).thenReturn(2);
		publisher.failAll.set(true);

		OutboxRelay.RunResult result = relay.runOnce();

		assertThat(result).isEqualTo(new OutboxRelay.RunResult(3, 0, 1, 0, 2));
		verify(store).markRetry(10L, Duration.ofSeconds(1));
		verify(store).release(List.of(11L, 12L));
		verify(store, never()).markSent(anyLong());
	}

	@Test
	void marksRowFailedAfterMaxAttemptsAndRunsMaintenanceAndMetricsRefresh() {
		when(store.claim(20, 100)).thenReturn(List.of(
				new OutboxStore.OutboxRow(99L, "order-99", "order.created", "{\"id\":99}", 100)));
		publisher.failAll.set(true);

		OutboxRelay.RunResult result = relay.runOnce();

		assertThat(result).isEqualTo(new OutboxRelay.RunResult(1, 0, 0, 1, 0));
		verify(store).markFailed(99L);

		when(store.reapStale(any(), anyInt())).thenReturn(1);
		when(store.deleteSentOlderThan(any(), anyInt())).thenReturn(5);
		maintenance.reap();
		maintenance.cleanup();
		verify(store).reapStale(Duration.ofMinutes(5), 100);
		verify(store).deleteSentOlderThan(Duration.ofDays(7), 1000);

		when(store.countPending()).thenReturn(3L);
		when(store.lagSeconds()).thenReturn(12.4);
		when(store.countFailed()).thenReturn(1L);
		metrics.refresh();
		assertThat(registry.get("outbox.pending").gauge().value()).isEqualTo(3.0);
		assertThat(registry.get("outbox.lag.seconds").gauge().value()).isEqualTo(12.0);
		assertThat(registry.get("outbox.failed").gauge().value()).isEqualTo(1.0);
	}

	static class RecordingPublisher implements OutboxPublisher {

		final List<String> sentAggregates = new ArrayList<>();

		final AtomicBoolean failAll = new AtomicBoolean(false);

		@Override
		public void send(String aggregateId, String eventType, String payload) {
			if (failAll.get()) {
				throw new IllegalStateException("broker unreachable");
			}
			sentAggregates.add(aggregateId);
		}

		void reset() {
			sentAggregates.clear();
			failAll.set(false);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ImportAutoConfiguration(ShopliteOutboxAutoConfiguration.class)
	static class TestApp {

		@Bean
		JdbcTemplate jdbcTemplate() {
			return mock(JdbcTemplate.class);
		}

		@Bean
		OutboxStore outboxStore() {
			return mock(OutboxStore.class);
		}

		@Bean
		SimpleMeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

		@Bean
		RecordingPublisher recordingPublisher() {
			return new RecordingPublisher();
		}

	}

}
