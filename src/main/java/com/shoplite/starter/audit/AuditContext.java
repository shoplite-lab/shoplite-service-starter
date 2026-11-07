package com.shoplite.starter.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Builds {@link AuditEvent}s with ambient actor identity and tracing context from the
 * Spring Security context and SLF4J MDC.
 */
public final class AuditContext {

	private AuditContext() {
	}

	public static AuditEvent buildEvent(String action, String targetType, String targetId, String outcome,
			Map<String, String> details) {
		String actorSub = "system";
		String actorRealm = "system";

		try {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth instanceof JwtAuthenticationToken jwtAuth) {
				Jwt jwt = jwtAuth.getToken();
				if (jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
					actorSub = jwt.getSubject();
				}
				String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : "";
				if (issuer.contains("shoplite-internal")) {
					actorRealm = "shoplite-internal";
				}
				else if (issuer.contains("shoplite")) {
					actorRealm = "shoplite";
				}
			}
			else if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
				actorSub = auth.getName();
			}
		}
		catch (NoClassDefFoundError ignored) {
			// Spring Security is optional on classpath
		}

		String traceId = MDC.get("traceId");
		if (traceId == null || traceId.isBlank()) {
			traceId = MDC.get("trace_id");
		}

		return new AuditEvent(UUID.randomUUID().toString(), AuditEvent.DEFAULT_EVENT_TYPE, Instant.now(), actorSub,
				actorRealm, action, targetType, targetId, outcome, traceId, details);
	}

}
