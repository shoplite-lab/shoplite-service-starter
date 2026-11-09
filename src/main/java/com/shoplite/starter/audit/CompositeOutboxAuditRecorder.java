package com.shoplite.starter.audit;

import com.shoplite.starter.outbox.OutboxStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes audit events to both structured logs and the transactional outbox table (ADR 0003, task 5.6).
 */
public class CompositeOutboxAuditRecorder implements AuditRecorder {

	private static final Logger log = LoggerFactory.getLogger(CompositeOutboxAuditRecorder.class);

	private final AuditRecorder delegate;
	private final OutboxStore outboxStore;
	private final ObjectMapper json;

	public CompositeOutboxAuditRecorder(AuditRecorder delegate, OutboxStore outboxStore, ObjectMapper json) {
		this.delegate = delegate;
		this.outboxStore = outboxStore;
		this.json = json;
	}

	@Override
	public void record(AuditEvent event) {
		// 1. Always emit structured log for instant Loki visibility
		delegate.record(event);

		// 2. Enqueue to transactional outbox for durable asynchronous delivery
		try {
			String payload = json.writeValueAsString(event);
			Object aggregateId = (event.targetId() != null && !event.targetId().isBlank())
					? event.targetId()
					: event.actorSub();
			outboxStore.enqueue(event.eventId(), aggregateId, event.eventType(), payload);
		}
		catch (Exception e) {
			log.error("Failed to enqueue audit event {} to outbox", event.eventId(), e);
			if (TransactionSynchronizationManager.isActualTransactionActive()) {
				throw (e instanceof RuntimeException re ? re : new IllegalStateException("Failed to enqueue audit event to outbox", e));
			}
		}
	}

}
