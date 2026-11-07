package com.shoplite.starter.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Emits exactly one structured access log line per HTTP request (ADR 0003, task 5.16/5.17).
 *
 * <p>Fields:
 * <ul>
 * <li>{@code http_method}: HTTP verb ({@code GET}, {@code POST}, ...)</li>
 * <li>{@code http_route}: MVC route template ({@code /api/orders/{id}}) rather than the raw URI so
 * cardinality stays bounded and path parameters never leak into logs; {@code UNMATCHED} when no
 * handler matched.</li>
 * <li>{@code http_status}: response status code</li>
 * <li>{@code duration_ms}: wall-clock duration in milliseconds</li>
 * <li>{@code sub}: opaque JWT subject, or {@code anonymous} when unauthenticated. Never logs
 * username, email, query string or IP.</li>
 * </ul>
 * {@code trace_id} and {@code span_id} are attached automatically from MDC by Spring Boot's
 * structured logging / OpenTelemetry Java agent.
 */
public class AccessLogFilter extends OncePerRequestFilter implements Ordered {

	public static final String LOGGER_NAME = "http.access";

	public static final String SUB_ATTRIBUTE = "shoplite.auth.sub";

	public static final String UNMATCHED_ROUTE = "UNMATCHED";

	private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

	private final boolean ignoreActuator;

	public AccessLogFilter(boolean ignoreActuator) {
		this.ignoreActuator = ignoreActuator;
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if (!ignoreActuator) {
			return false;
		}
		String uri = request.getRequestURI();
		return uri != null && (uri.equals("/actuator") || uri.startsWith("/actuator/"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long startNanos = System.nanoTime();
		try {
			chain.doFilter(request, response);
		}
		finally {
			long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
			log.atInfo()
				.addKeyValue("http_method", request.getMethod())
				.addKeyValue("http_route", resolveRoute(request))
				.addKeyValue("http_status", response.getStatus())
				.addKeyValue("duration_ms", durationMs)
				.addKeyValue("sub", resolveSubject(request))
				.log("http_request");
		}
	}

	private static String resolveRoute(HttpServletRequest request) {
		Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (pattern != null) {
			String route = pattern.toString();
			if (StringUtils.hasText(route)) {
				return route;
			}
		}
		return UNMATCHED_ROUTE;
	}

	private static String resolveSubject(HttpServletRequest request) {
		Object captured = request.getAttribute(SUB_ATTRIBUTE);
		if (captured instanceof String sub && StringUtils.hasText(sub)) {
			return sub;
		}
		try {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth instanceof JwtAuthenticationToken token && StringUtils.hasText(token.getToken().getSubject())) {
				return token.getToken().getSubject();
			}
		}
		catch (NoClassDefFoundError ignored) {
			// Spring Security is optional on the classpath.
		}
		return "anonymous";
	}

}
