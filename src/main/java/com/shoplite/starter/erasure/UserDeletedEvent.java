package com.shoplite.starter.erasure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code identity.user-deleted} event envelope (producer: identity-service, ADR 0002). Unknown
 * fields are ignored (tolerant reader). Carries only the opaque {@code userId}, never a username
 * or email.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDeletedEvent(String eventId, String eventType, String occurredAt, Payload payload) {

	public static final String EVENT_TYPE = "identity.user-deleted";

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Payload(String userId, String realm, String source) {
	}

	public String userId() {
		return payload == null ? null : payload.userId();
	}

}
