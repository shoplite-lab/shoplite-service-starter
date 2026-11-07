package com.shoplite.starter.outbox;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Housekeeping for the transactional outbox: recovers rows stuck in {@code SENDING} after a crash
 * and purges old {@code SENT} rows. {@code FAILED} rows are kept for inspection and manual replay.
 */
public class OutboxMaintenance {

	private static final Logger log = LoggerFactory.getLogger(OutboxMaintenance.class);

	private final boolean enabled;

	private final Duration staleAfter;

	private final Duration retention;

	private final int maxAttempts;

	private final OutboxStore store;

	public OutboxMaintenance(boolean enabled, Duration staleAfter, Duration retention, int maxAttempts,
			OutboxStore store) {
		this.enabled = enabled;
		this.staleAfter = staleAfter;
		this.retention = retention;
		this.maxAttempts = maxAttempts;
		this.store = store;
	}

	@Scheduled(fixedDelayString = "${shoplite.outbox.reap-interval:30s}",
			initialDelayString = "${shoplite.outbox.reap-interval:30s}")
	public void reap() {
		if (!enabled) {
			return;
		}
		int recovered = store.reapStale(staleAfter, maxAttempts);
		if (recovered > 0) {
			log.warn("Recovered {} outbox row(s) stuck in SENDING for more than {}", recovered, staleAfter);
		}
	}

	@Scheduled(cron = "${shoplite.outbox.cleanup-cron:0 30 3 * * *}")
	public void cleanup() {
		if (!enabled) {
			return;
		}
		int total = 0;
		int deleted;
		do {
			deleted = store.deleteSentOlderThan(retention, 1000);
			total += deleted;
		}
		while (deleted == 1000);
		if (total > 0) {
			log.info("Deleted {} SENT outbox row(s) older than {}", total, retention);
		}
	}

}
