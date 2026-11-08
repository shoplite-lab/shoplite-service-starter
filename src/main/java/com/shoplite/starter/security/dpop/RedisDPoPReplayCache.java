package com.shoplite.starter.security.dpop;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Distributed Redis-backed {@link DPoPReplayCache} using atomic {@code SETNX} operations with TTL.
 * Ensures consistent anti-replay protection across multiple microservice pods on Kubernetes.
 */
public class RedisDPoPReplayCache implements DPoPReplayCache {

	private static final Logger log = LoggerFactory.getLogger(RedisDPoPReplayCache.class);

	private static final String KEY_PREFIX = "dpop:jti:";

	private final StringRedisTemplate redisTemplate;

	private final InMemoryDPoPReplayCache fallbackCache = new InMemoryDPoPReplayCache();

	public RedisDPoPReplayCache(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public boolean recordIfAbsent(String jti, Duration ttl) {
		if (jti == null || jti.isBlank()) {
			return false;
		}

		String key = KEY_PREFIX + jti;
		try {
			Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
			return Boolean.TRUE.equals(wasSet);
		}
		catch (Exception ex) {
			log.warn("Redis unreachable for DPoP replay check, falling back to in-memory cache: {}", ex.getMessage());
			return fallbackCache.recordIfAbsent(jti, ttl);
		}
	}

}
