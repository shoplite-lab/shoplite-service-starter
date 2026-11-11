package com.shoplite.starter.security.dpop;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Servlet filter verifying RFC 9449 DPoP proofs for sender-constrained JWT access tokens.
 *
 * <p>Execution behavior:
 * <ul>
 * <li>If the request is unauthenticated or the token has no {@code cnf.jkt} claim (and strict mode is
 * disabled), the request passes through untouched (preserving backward compatibility with CLI device
 * flow scripts and automated drills).</li>
 * <li>If the token has {@code cnf.jkt} (or strict mode is enabled), a valid matching {@code DPoP}
 * proof header is strictly required. Any mismatch results in {@code 401 Unauthorized} with an RFC 9457
 * problem detail and a standard {@code WWW-Authenticate: DPoP} header.</li>
 * </ul>
 */
public class ShopliteDPoPFilter extends OncePerRequestFilter {

	private static final String HEADER_DPOP = "DPoP";

	private static final String HEADER_WWW_AUTHENTICATE = "WWW-Authenticate";

	private final DPoPValidator validator;

	private final ShopliteDPoPProperties properties;

	private final ObjectMapper json;

	public ShopliteDPoPFilter(DPoPValidator validator, ShopliteDPoPProperties properties, ObjectMapper json) {
		this.validator = validator;
		this.properties = properties != null ? properties : new ShopliteDPoPProperties();
		this.json = json != null ? json : new ObjectMapper();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		if (!properties.isEnabled()) {
			filterChain.doFilter(request, response);
			return;
		}

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
			// Unauthenticated request or non-JWT authentication (e.g. public endpoints, /actuator)
			filterChain.doFilter(request, response);
			return;
		}

		Jwt jwt = jwtAuth.getToken();
		Map<String, Object> cnf = jwt.getClaimAsMap("cnf");
		if (cnf == null) {
			cnf = jwt.getClaimAsMap("dpop_cnf");
		}
		String jkt = cnf != null ? (String) cnf.get("jkt") : null;

		boolean isDPoPBound = jkt != null && !jkt.isBlank();
		boolean mustValidate = isDPoPBound || properties.isStrict();

		if (!mustValidate) {
			filterChain.doFilter(request, response);
			return;
		}

		String dpopHeader = request.getHeader(HEADER_DPOP);
		if (dpopHeader == null || dpopHeader.isBlank()) {
			writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "invalid_dpop_proof",
					"DPoP proof required for sender-constrained token");
			return;
		}

		try {
			validator.validate(dpopHeader, jwt.getTokenValue(), request.getMethod(), request.getRequestURI(), jkt);
		}
		catch (DPoPValidationException ex) {
			writeErrorResponse(response, HttpStatus.UNAUTHORIZED, ex.getErrorCode(), ex.getMessage());
			return;
		}

		filterChain.doFilter(request, response);
	}

	private void writeErrorResponse(HttpServletResponse response, HttpStatus status, String errorCode, String detail)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setHeader(HEADER_WWW_AUTHENTICATE, "DPoP error=\"" + errorCode + "\", error_description=\"" + detail + "\"");

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("urn:shoplite:dpop:" + errorCode));
		problem.setTitle("DPoP Proof Validation Failed");

		byte[] body = json.writeValueAsBytes(problem);
		response.getOutputStream().write(body);
	}

}
