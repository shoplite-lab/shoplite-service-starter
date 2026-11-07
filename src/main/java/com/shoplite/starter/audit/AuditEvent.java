package com.shoplite.starter.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable audit record for staff/security actions across ShopLite services (ADR 0003, task 5.6).
 *
 * <p>Must never contain personal data (username, email, phone, address): {@code actorSub} and
 * {@code targetId} are opaque identifiers.
 */
public record AuditEvent(String actorSub, String actorRealm, String action, String targetType, String targetId,
		String outcome, Instant occurredAt, Map<String, String> details) {

	private static final Set<String> FORBIDDEN_DETAIL_KEYS = Set.of("username", "email", "phone", "address", "fullName",
			"password");

	public AuditEvent {
		Objects.requireNonNull(actorSub, "actorSub is required");
		Objects.requireNonNull(action, "action is required");
		Objects.requireNonNull(outcome, "outcome is required");
		occurredAt = occurredAt == null ? Instant.now() : occurredAt;
		details = details == null ? Map.of() : Map.copyOf(details);
		for (String key : details.keySet()) {
			if (FORBIDDEN_DETAIL_KEYS.contains(key)) {
				throw new IllegalArgumentException("Audit details must not carry personal data key: " + key);
			}
		}
	}

	public static AuditEvent of(String actorSub, String actorRealm, String action, String targetType, String targetId,
			String outcome) {
		return new AuditEvent(actorSub, actorRealm, action, targetType, targetId, outcome, Instant.now(), Map.of());
	}

}
