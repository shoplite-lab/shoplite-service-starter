package com.shoplite.starter.erasure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tombstones of deleted accounts (ADR 0002) and the per-subject advisory lock.
 *
 * <p>Why a tombstone: Keycloak deletes the account first, but the customer's access token stays
 * valid for up to 300 s (Kong checks JWTs offline). Without it, a request in that window would
 * write new personal data after the erasure ran.
 *
 * <p>Why a lock: a request can pass the tombstone check, the erasure can commit, and only then the
 * request inserts its row. Every write path that creates personal data for a subject, and the
 * erasure itself, take {@link #lock} first and re-check {@link #isErased} inside that transaction.
 */
public class ErasedSubjects {

	public static final String DELETED_DETAIL = "This account was deleted";

	private final JdbcTemplate jdbc;

	public ErasedSubjects(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Transaction-scoped advisory lock on the subject, released at commit/rollback. {@code MANDATORY}:
	 * outside a transaction the lock would be released at once and protect nothing.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lock(String sub) {
		jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", (ResultSetExtractor<Void>) rs -> null,
				sub);
	}

	/** A primary-key lookup; cheap enough to run on every authenticated request at this scale. */
	public boolean isErased(String sub) {
		if (sub == null) {
			return false;
		}
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM erased_subjects WHERE sub = ?)", Boolean.class, sub));
	}

	/** Inserts the tombstone row inside an existing transaction; idempotent. */
	public void markErased(String sub) {
		jdbc.update("INSERT INTO erased_subjects (sub) VALUES (?) ON CONFLICT (sub) DO NOTHING", sub);
	}

	/** Takes the subject lock and records the tombstone in a transaction; idempotent. */
	@Transactional
	public void record(String sub) {
		lock(sub);
		markErased(sub);
	}

}
