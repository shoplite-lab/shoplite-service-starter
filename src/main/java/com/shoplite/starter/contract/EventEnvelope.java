package com.shoplite.starter.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard ShopLite event envelope for outbox and Kafka messages.
 *
 * @param <T> Domain payload type
 */
public record EventEnvelope<T>(
		String eventId,
		String eventType,
		String schemaVersion,
		Instant occurredAt,
		String aggregateId,
		T payload) {

	public static final String DEFAULT_SCHEMA_VERSION = "1.0";

	public static <T> EventEnvelope<T> of(String eventType, String aggregateId, T payload) {
		return new EventEnvelope<>(
				UUID.randomUUID().toString(),
				eventType,
				DEFAULT_SCHEMA_VERSION,
				Instant.now(),
				aggregateId,
				payload);
	}

}
