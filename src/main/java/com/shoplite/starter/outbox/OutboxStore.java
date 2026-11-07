package com.shoplite.starter.outbox;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claim/acknowledge operations of the transactional outbox (ADR 0003). Claiming uses
 * {@code FOR UPDATE SKIP LOCKED}, so several replicas can run {@link OutboxRelay} concurrently
 * without sending the same row twice, and no row lock is held across the Kafka send.
 */
public class OutboxStore {

	private final JdbcTemplate jdbc;

	public OutboxStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record OutboxRow(long id, String aggregateId, String eventType, String payload, int attempts) {
	}

	/** Leases due rows: PENDING -> SENDING, attempts incremented, one short transaction. */
	@Transactional
	public List<OutboxRow> claim(int batchSize, int maxAttempts) {
		return jdbc.query("""
				UPDATE outbox
				SET status = 'SENDING',
				    claimed_at = now(),
				    attempts = attempts + 1
				WHERE id IN (
				    SELECT id FROM outbox
				    WHERE status = 'PENDING'
				      AND next_attempt_at <= now()
				      AND attempts < ?
				    ORDER BY id
				    LIMIT ?
				    FOR UPDATE SKIP LOCKED
				)
				RETURNING id, aggregate_id, event_type, payload, attempts
				""",
				(rs, rowNum) -> new OutboxRow(
						rs.getLong("id"),
						rs.getString("aggregate_id"),
						rs.getString("event_type"),
						rs.getString("payload"),
						rs.getInt("attempts")),
				maxAttempts, batchSize);
	}

	@Transactional
	public int markSent(long id) {
		return jdbc.update("""
				UPDATE outbox
				SET status = 'SENT', sent_at = now(), claimed_at = NULL
				WHERE id = ? AND status = 'SENDING'
				""", id);
	}

	@Transactional
	public int markRetry(long id, Duration delay) {
		return jdbc.update("""
				UPDATE outbox
				SET status = 'PENDING',
				    next_attempt_at = now() + make_interval(secs => ?),
				    claimed_at = NULL
				WHERE id = ? AND status = 'SENDING'
				""", (int) delay.toSeconds(), id);
	}

	/**
	 * Gives back claimed rows the relay did not try (it stopped the pass early): PENDING again,
	 * due at once, and the attempt counted by {@link #claim} is returned since nothing was sent.
	 */
	@Transactional
	public int release(List<Long> ids) {
		if (ids.isEmpty()) {
			return 0;
		}
		int[] updated = jdbc.batchUpdate("""
				UPDATE outbox
				SET status = 'PENDING', attempts = attempts - 1, claimed_at = NULL
				WHERE id = ? AND status = 'SENDING'
				""", ids.stream().map(id -> new Object[] { id }).toList());
		return Arrays.stream(updated).sum();
	}

	@Transactional
	public int markFailed(long id) {
		return jdbc.update("""
				UPDATE outbox
				SET status = 'FAILED', claimed_at = NULL
				WHERE id = ? AND status = 'SENDING'
				""", id);
	}

	/** Recovers rows stuck in SENDING after a relay crash. */
	@Transactional
	public int reapStale(Duration age, int maxAttempts) {
		return jdbc.update("""
				UPDATE outbox
				SET status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
				    next_attempt_at = now(),
				    claimed_at = NULL
				WHERE status = 'SENDING'
				  AND claimed_at < now() - make_interval(secs => ?)
				""", maxAttempts, (int) age.toSeconds());
	}

	/** Deletes at most {@code batchSize} old SENT rows; call in a loop to keep locks short. */
	@Transactional
	public int deleteSentOlderThan(Duration age, int batchSize) {
		return jdbc.update("""
				DELETE FROM outbox
				WHERE id IN (
				    SELECT id FROM outbox
				    WHERE status = 'SENT'
				      AND sent_at < now() - make_interval(secs => ?)
				    ORDER BY id
				    LIMIT ?
				)
				""", (int) age.toSeconds(), batchSize);
	}

	public long countPending() {
		Long count = jdbc.queryForObject(
				"SELECT count(*) FROM outbox WHERE status IN ('PENDING', 'SENDING')", Long.class);
		return count == null ? 0 : count;
	}

	public long countFailed() {
		Long count = jdbc.queryForObject("SELECT count(*) FROM outbox WHERE status = 'FAILED'", Long.class);
		return count == null ? 0 : count;
	}

	/** Age of the oldest unsent event; 0 when the outbox is empty. */
	public double lagSeconds() {
		Double lag = jdbc.queryForObject("""
				SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0)
				FROM outbox
				WHERE status IN ('PENDING', 'SENDING')
				""", Double.class);
		return lag == null ? 0 : lag;
	}

}
