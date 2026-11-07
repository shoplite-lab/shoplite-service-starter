package com.shoplite.starter.audit;

import java.util.Map;

/**
 * Records security and staff actions across ShopLite services (ADR 0003, task 5.6).
 */
public interface AuditRecorder {

	void record(AuditEvent event);

	default void record(String action, String targetType, String targetId, String outcome,
			Map<String, String> details) {
		record(AuditContext.buildEvent(action, targetType, targetId, outcome, details));
	}

	default void record(String action, String targetType, String targetId, String outcome) {
		record(action, targetType, targetId, outcome, Map.of());
	}

}
