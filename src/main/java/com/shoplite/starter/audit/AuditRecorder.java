package com.shoplite.starter.audit;

/**
 * Records security and staff actions across ShopLite services (ADR 0003, task 5.6).
 */
@FunctionalInterface
public interface AuditRecorder {

	void record(AuditEvent event);

}
