package com.shoplite.starter.outbox;

/**
 * Sends a claimed outbox row to Kafka (or another transport). Implementations are invoked outside
 * any database transaction by {@link OutboxRelay}.
 */
@FunctionalInterface
public interface OutboxPublisher {

	void send(String aggregateId, String eventType, String payload) throws Exception;

	default void send(String aggregateId, String eventType, String payload, String traceparent) throws Exception {
		send(aggregateId, eventType, payload);
	}

}
