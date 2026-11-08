package com.shoplite.starter.security.dpop;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory implementation of {@link DPoPReplayCache} using a concurrent map with
 * timestamp-based expiration. Ideal for standalone mode, tests, and microservices without Redis.
 */
public class InMemoryDPoPReplayCache implements DPoPReplayCache {

	private final ConcurrentMap<String, Instant> cache = new ConcurrentHashMap<>();

	@Override
	public boolean recordIfAbsent(String jti, Duration ttl) {
		if (jti == null || jti.isBlank()) {
			return false;
		}

		Instant now = Instant.now();
		Instant expiresAt = now.plus(ttl);

		// Opportunistic cleanup if cache grows
		if (cache.size() > 1000) {
			cleanup(now);
		}

		Instant existing = cache.putIfAbsent(jti, expiresAt);
		if (existing == null) {
			return true;
		}

		// If existing entry has expired, overwrite it
		if (existing.isBefore(now)) {
			return cache.replace(jti, existing, expiresAt);
		}

		return false;
	}

	private void cleanup(Instant now) {
		cache.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
	}

	/** Returns current cache size (useful for unit testing). */
	public int size() {
		return cache.size();
	}

}
