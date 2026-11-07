package com.shoplite.starter.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Default {@link AuditRecorder} that writes structured JSON fields to the {@code audit} logger.
 * When task 5.6 introduces centralized outbox-backed audit events, services calling
 * {@link AuditRecorder} will not need code changes.
 */
public class StructuredLogAuditRecorder implements AuditRecorder {

	public static final String LOGGER_NAME = "audit";

	private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

	@Override
	public void record(AuditEvent event) {
		LoggingEventBuilder builder = log.atInfo()
			.addKeyValue("actor_sub", event.actorSub())
			.addKeyValue("actor_realm", event.actorRealm() == null ? "" : event.actorRealm())
			.addKeyValue("action", event.action())
			.addKeyValue("target_type", event.targetType() == null ? "" : event.targetType())
			.addKeyValue("target_id", event.targetId() == null ? "" : event.targetId())
			.addKeyValue("outcome", event.outcome())
			.addKeyValue("occurred_at", event.occurredAt().toString());
		event.details().forEach(builder::addKeyValue);
		builder.log("audit_event");
	}

}
