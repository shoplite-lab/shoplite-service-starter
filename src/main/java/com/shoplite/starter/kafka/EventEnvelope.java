package com.shoplite.starter.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.UUID;

/**
 * Standard ShopLite Kafka event envelope (root {@code README.md}, ADR 0003).
 *
 * @param <T> payload type owned by the producing/consuming service (never a shared business DTO)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope<T>(String eventId, String eventType, Instant occurredAt, T payload) {

	public static <T> EventEnvelope<T> of(String eventType, T payload) {
		return new EventEnvelope<>(UUID.randomUUID().toString(), eventType, Instant.now(), payload);
	}

	public static <T> EventEnvelope<T> of(String eventId, String eventType, Instant occurredAt, T payload) {
		return new EventEnvelope<>(eventId, eventType, occurredAt, payload);
	}

}
