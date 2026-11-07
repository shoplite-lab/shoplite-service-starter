package com.shoplite.starter.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Outbox Prometheus metrics ({@code outbox_pending}, {@code outbox_lag_seconds},
 * {@code outbox_failed}, {@code outbox_sent_total}).
 */
public class OutboxMetrics {

	private final OutboxStore store;

	private final AtomicLong pending = new AtomicLong();

	private final AtomicLong lagSeconds = new AtomicLong();

	private final AtomicLong failed = new AtomicLong();

	private final Counter sent;

	public OutboxMetrics(MeterRegistry registry, OutboxStore store) {
		this.store = store;
		Gauge.builder("outbox.pending", pending, AtomicLong::get)
			.description("Outbox rows waiting to be sent (PENDING or SENDING)")
			.register(registry);
		Gauge.builder("outbox.lag.seconds", lagSeconds, AtomicLong::get)
			.description("Age of the oldest unsent outbox row, in seconds")
			.register(registry);
		Gauge.builder("outbox.failed", failed, AtomicLong::get)
			.description("Outbox rows that exhausted their attempts")
			.register(registry);
		this.sent = Counter.builder("outbox.sent")
			.description("Outbox rows successfully sent to Kafka")
			.register(registry);
	}

	@Scheduled(fixedDelayString = "${shoplite.outbox.metrics-interval:15s}",
			initialDelayString = "${shoplite.outbox.metrics-interval:15s}")
	public void refresh() {
		pending.set(store.countPending());
		lagSeconds.set(Math.round(store.lagSeconds()));
		failed.set(store.countFailed());
	}

	public void recordSent() {
		sent.increment();
	}

}
