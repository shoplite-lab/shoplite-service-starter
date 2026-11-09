package com.shoplite.starter.outbox;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Polls the outbox and sends each due row via {@link OutboxPublisher}, outside any database
 * transaction (ADR 0003).
 *
 * <p>Stops at the first failed send and releases the untried rows back to {@code PENDING} so a
 * broker outage blocks the scheduler thread for at most one send timeout per pass.
 */
public class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	/** Indexed by attempt number: 1 -> 1s, 2 -> 5s, 3 -> 30s, 4 -> 2m, 5+ -> 5m. */
	private static final Duration[] BACKOFF = { Duration.ofSeconds(1), Duration.ofSeconds(5),
			Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(5) };

	private final boolean enabled;

	private final int batchSize;

	private final int maxAttempts;

	private final OutboxStore store;

	private final OutboxPublisher publisher;

	private final OutboxMetrics metrics;

	public OutboxRelay(boolean enabled, int batchSize, int maxAttempts, OutboxStore store,
			OutboxPublisher publisher, OutboxMetrics metrics) {
		this.enabled = enabled;
		this.batchSize = batchSize;
		this.maxAttempts = maxAttempts;
		this.store = store;
		this.publisher = publisher;
		this.metrics = metrics;
	}

	public record RunResult(int claimed, int sent, int retried, int failed, int released) {
	}

	@Scheduled(fixedDelayString = "${shoplite.outbox.poll-interval:1s}",
			initialDelayString = "${shoplite.outbox.initial-delay:1s}")
	public void poll() {
		if (!enabled) {
			return;
		}
		runOnce();
	}

	public RunResult runOnce() {
		var rows = store.claim(batchSize, maxAttempts);
		if (rows.isEmpty()) {
			return new RunResult(0, 0, 0, 0, 0);
		}
		int sent = 0;
		int retried = 0;
		int failed = 0;
		int released = 0;
		for (int i = 0; i < rows.size(); i++) {
			OutboxStore.OutboxRow row = rows.get(i);
			try {
				publisher.send(row.aggregateId(), row.eventType(), row.payload(), row.traceparent());
				store.markSent(row.id());
				metrics.recordSent();
				sent++;
				continue;
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				store.markRetry(row.id(), backoff(row.attempts()));
				retried++;
			}
			catch (Exception e) {
				if (row.attempts() >= maxAttempts) {
					store.markFailed(row.id());
					failed++;
					log.error("Outbox row {} for aggregate {} failed after {} attempts", row.id(), row.aggregateId(),
							row.attempts(), e);
				}
				else {
					store.markRetry(row.id(), backoff(row.attempts()));
					retried++;
					log.warn("Outbox send failed for aggregate {} (attempt {}), will retry: {}", row.aggregateId(),
							row.attempts(), e.getMessage());
				}
			}
			released = store.release(rows.subList(i + 1, rows.size()).stream().map(OutboxStore.OutboxRow::id).toList());
			break;
		}
		if (sent > 0 || retried > 0 || failed > 0) {
			log.info("Outbox pass: claimed {}, sent {}, retried {}, failed {}, released {}", rows.size(), sent,
					retried, failed, released);
		}
		return new RunResult(rows.size(), sent, retried, failed, released);
	}

	private static Duration backoff(int attempts) {
		return BACKOFF[Math.min(Math.max(attempts, 1), BACKOFF.length) - 1];
	}

}
