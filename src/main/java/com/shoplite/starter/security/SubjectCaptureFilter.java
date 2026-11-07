package com.shoplite.starter.security;

import com.shoplite.starter.web.AccessLogFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Records the authenticated JWT {@code sub} as a request attribute right after bearer authentication
 * so {@link AccessLogFilter} can log it after {@code SecurityContextHolderFilter} has cleared the
 * security context on response exit.
 *
 * <p>Not a bean: added directly to the security filter chain.
 */
public class SubjectCaptureFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth instanceof JwtAuthenticationToken token) {
			String sub = token.getToken().getSubject();
			if (StringUtils.hasText(sub)) {
				request.setAttribute(AccessLogFilter.SUB_ATTRIBUTE, sub);
			}
		}
		chain.doFilter(request, response);
	}

}
