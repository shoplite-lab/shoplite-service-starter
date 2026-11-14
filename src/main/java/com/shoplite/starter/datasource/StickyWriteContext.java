package com.shoplite.starter.datasource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Tracks the most recent write timestamp per actor so subsequent read-only queries by the same actor
 * continue routing to the primary writer for a short sticky window, preventing replication-lag
 * read-your-own-write anomalies.
 */
public class StickyWriteContext {

	private final ConcurrentMap<String, Instant> lastWriteByActor = new ConcurrentHashMap<>();

	private final Clock clock;

	public StickyWriteContext() {
		this(Clock.systemUTC());
	}

	public StickyWriteContext(Clock clock) {
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
	}

	public void markWrite() {
		markWrite(resolveCurrentActor());
	}

	public void markWrite(String actor) {
		if (actor == null || actor.isBlank()) {
			return;
		}
		lastWriteByActor.put(actor, clock.instant());
	}

	public boolean shouldForceWriter(Duration stickyDuration) {
		return shouldForceWriter(resolveCurrentActor(), stickyDuration);
	}

	public boolean shouldForceWriter(String actor, Duration stickyDuration) {
		if (actor == null || actor.isBlank() || stickyDuration == null || stickyDuration.isZero()
				|| stickyDuration.isNegative()) {
			return false;
		}
		Instant writtenAt = lastWriteByActor.get(actor);
		if (writtenAt == null) {
			return false;
		}
		Instant now = clock.instant();
		if (writtenAt.plus(stickyDuration).isAfter(now)) {
			return true;
		}
		lastWriteByActor.remove(actor, writtenAt);
		return false;
	}

	public void clear() {
		lastWriteByActor.clear();
	}

	public String resolveCurrentActor() {
		String mdcSub = MDC.get("sub");
		if (mdcSub != null && !mdcSub.isBlank()) {
			return "sub:" + mdcSub;
		}

		try {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth instanceof JwtAuthenticationToken jwtAuth) {
				String subject = jwtAuth.getToken().getSubject();
				if (subject != null && !subject.isBlank()) {
					return "sub:" + subject;
				}
			}
			else if (auth != null && auth.isAuthenticated() && auth.getName() != null && !auth.getName().isBlank()
					&& !"anonymousUser".equals(auth.getName())) {
				return "sub:" + auth.getName();
			}
		}
		catch (NoClassDefFoundError ignored) {
			// Spring Security is optional on classpath
		}

		return "thread:" + Thread.currentThread().threadId();
	}

}
