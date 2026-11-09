package com.shoplite.starter.outbox;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claim/acknowledge operations of the transactional outbox (ADR 0003). Claiming uses
 * {@code FOR UPDATE SKIP LOCKED}, so several replicas can run {@link OutboxRelay} concurrently
 * without sending the same row twice, and no row lock is held across the Kafka send.
 */
public class OutboxStore {

	private final JdbcTemplate jdbc;

	private volatile boolean traceparentColumnSupported = true;

	public OutboxStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record OutboxRow(long id, String aggregateId, String eventType, String payload, int attempts, String traceparent) {
		public OutboxRow(long id, String aggregateId, String eventType, String payload, int attempts) {
			this(id, aggregateId, eventType, payload, attempts, null);
		}
	}

	/**
	 * Enqueues an event into the transactional outbox, capturing the current traceparent from MDC.
	 */
	@Transactional
	public int enqueue(String eventId, Object aggregateId, String eventType, String payload) {
		return enqueue(eventId, aggregateId, eventType, payload, resolveCurrentTraceparent());
	}

	/**
	 * Enqueues an event into the transactional outbox with an explicit traceparent.
	 * aggregateId can be Long or String; non-numeric strings are hashed to positive Longs
	 * to prevent SQL type errors on databases with BIGINT aggregate_id columns.
	 */
	@Transactional
	public int enqueue(String eventId, Object aggregateId, String eventType, String payload, String traceparent) {
		Object convertedAggregateId = aggregateId;
		if (aggregateId instanceof String s) {
			try {
				convertedAggregateId = Long.parseLong(s);
			}
			catch (NumberFormatException ignored) {
				convertedAggregateId = Math.abs((long) s.hashCode());
			}
		}

		if (traceparentColumnSupported) {
			try {
				return jdbc.update("""
						INSERT INTO outbox (event_id, aggregate_id, event_type, payload, traceparent)
						VALUES (?, ?, ?, ?, ?)
						ON CONFLICT (event_id) DO NOTHING
						""", eventId, convertedAggregateId, eventType, payload, traceparent);
			}
			catch (BadSqlGrammarException ex) {
				traceparentColumnSupported = false;
			}
		}

		return jdbc.update("""
				INSERT INTO outbox (event_id, aggregate_id, event_type, payload)
				VALUES (?, ?, ?, ?)
				ON CONFLICT (event_id) DO NOTHING
				""", eventId, convertedAggregateId, eventType, payload);
	}

	/**
	 * Leases due rows: PENDING -> SENDING, attempts incremented, one short transaction.
	 * Preserves aggregate-level FIFO ordering: a newer row for an aggregate is deferred if an earlier
	 * row of the same aggregate is still PENDING (backed off) or in-flight (SENDING).
	 */
	@Transactional
	public List<OutboxRow> claim(int batchSize, int maxAttempts) {
		if (traceparentColumnSupported) {
			try {
				return jdbc.query("""
						UPDATE outbox
						SET status = 'SENDING',
						    claimed_at = now(),
						    attempts = attempts + 1
						WHERE id IN (
						    SELECT o.id FROM outbox o
						    WHERE o.status = 'PENDING'
						      AND o.next_attempt_at <= now()
						      AND o.attempts < ?
						      AND NOT EXISTS (
						          SELECT 1 FROM outbox earlier
						          WHERE earlier.aggregate_id = o.aggregate_id
						            AND earlier.id < o.id
						            AND earlier.status IN ('PENDING', 'SENDING')
						      )
						    ORDER BY o.id
						    LIMIT ?
						    FOR UPDATE SKIP LOCKED
						)
						RETURNING id, aggregate_id, event_type, payload, attempts, traceparent
						""",
						(rs, rowNum) -> new OutboxRow(
								rs.getLong("id"),
								rs.getString("aggregate_id"),
								rs.getString("event_type"),
								rs.getString("payload"),
								rs.getInt("attempts"),
								rs.getString("traceparent")),
						maxAttempts, batchSize);
			}
			catch (BadSqlGrammarException ex) {
				traceparentColumnSupported = false;
			}
		}

		return jdbc.query("""
				UPDATE outbox
				SET status = 'SENDING',
				    claimed_at = now(),
				    attempts = attempts + 1
				WHERE id IN (
				    SELECT o.id FROM outbox o
				    WHERE o.status = 'PENDING'
				      AND o.next_attempt_at <= now()
				      AND o.attempts < ?
				      AND NOT EXISTS (
				          SELECT 1 FROM outbox earlier
				          WHERE earlier.aggregate_id = o.aggregate_id
				            AND earlier.id < o.id
				            AND earlier.status IN ('PENDING', 'SENDING')
				      )
				    ORDER BY o.id
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
						rs.getInt("attempts"),
						null),
				maxAttempts, batchSize);
	}

	private String resolveCurrentTraceparent() {
		String tp = MDC.get("traceparent");
		if (tp != null && !tp.isBlank()) {
			return tp;
		}
		String traceId = MDC.get("trace_id");
		if (traceId == null || traceId.isBlank()) {
			traceId = MDC.get("traceId");
		}
		String spanId = MDC.get("span_id");
		if (spanId == null || spanId.isBlank()) {
			spanId = MDC.get("spanId");
		}
		if (traceId != null && !traceId.isBlank() && spanId != null && !spanId.isBlank()) {
			return "00-" + traceId + "-" + spanId + "-01";
		}
		return null;
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
