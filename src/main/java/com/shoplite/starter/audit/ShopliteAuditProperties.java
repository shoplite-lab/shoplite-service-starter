package com.shoplite.starter.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shoplite.audit")
public class ShopliteAuditProperties {

	/**
	 * Whether to enqueue audit events to the transactional outbox table.
	 * Requires OutboxStore and ObjectMapper beans.
	 */
	private boolean outboxEnabled = false;

	public boolean isOutboxEnabled() {
		return outboxEnabled;
	}

	public void setOutboxEnabled(boolean outboxEnabled) {
		this.outboxEnabled = outboxEnabled;
	}

}
