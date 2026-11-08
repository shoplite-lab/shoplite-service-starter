package com.shoplite.starter.security.dpop;

import java.time.Duration;

/**
 * Replay protection cache for DPoP proof {@code jti} claims (RFC 9449 Section 4.3).
 */
public interface DPoPReplayCache {

	/**
	 * Records a DPoP proof identifier ({@code jti}) with an expiration TTL.
	 *
	 * @param jti unique identifier of the DPoP proof
	 * @param ttl retention duration (typically 2x max clock skew)
	 * @return {@code true} if successfully recorded (first time seen), {@code false} if already recorded (replay detected)
	 */
	boolean recordIfAbsent(String jti, Duration ttl);

}
