package com.shoplite.starter.erasure;

import com.shoplite.starter.web.AccessLogFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 403 for any authenticated request whose JWT {@code sub} was erased (ADR 0002). Runs once for the
 * whole API, right after bearer authentication, so no controller has to remember it.
 *
 * <p>The token of a deleted account stays valid for up to 300 s; Kong cannot know the account is
 * gone. Unauthenticated requests (actuator) pass untouched.
 *
 * <p>Not a {@code @Component}: Spring Boot would also register it as a plain servlet filter, outside
 * the security chain.
 */
public class ErasedSubjectFilter extends OncePerRequestFilter {

	private final ErasedSubjects erasedSubjects;

	private final ObjectMapper json;

	public ErasedSubjectFilter(ErasedSubjects erasedSubjects, ObjectMapper json) {
		this.erasedSubjects = erasedSubjects;
		this.json = json;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken token) {
			String sub = token.getToken().getSubject();
			if (StringUtils.hasText(sub)) {
				request.setAttribute(AccessLogFilter.SUB_ATTRIBUTE, sub);
			}
			if (erasedSubjects.isErased(sub)) {
				Map<String, Object> problem = new LinkedHashMap<>();
				problem.put("type", "about:blank");
				problem.put("title", HttpStatus.FORBIDDEN.getReasonPhrase());
				problem.put("status", HttpStatus.FORBIDDEN.value());
				problem.put("detail", ErasedSubjects.DELETED_DETAIL);
				problem.put("instance", request.getRequestURI());
				response.setStatus(HttpStatus.FORBIDDEN.value());
				response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
				json.writeValue(response.getOutputStream(), problem);
				return;
			}
		}
		chain.doFilter(request, response);
	}

}
