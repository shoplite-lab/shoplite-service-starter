package com.shoplite.starter.security.dpop;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ShopliteDPoPFilterTests {

	private ECKey clientKey;
	private String clientJkt;
	private DPoPValidator validator;
	private ShopliteDPoPProperties properties;
	private ShopliteDPoPFilter filter;
	private FilterChain chain;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() throws Exception {
		clientKey = new ECKeyGenerator(Curve.P_256).generate();
		clientJkt = clientKey.toPublicJWK().computeThumbprint("SHA-256").toString();
		validator = new DPoPValidator(new InMemoryDPoPReplayCache(), Duration.ofSeconds(60));
		properties = new ShopliteDPoPProperties();
		objectMapper = new ObjectMapper();
		filter = new ShopliteDPoPFilter(validator, properties, objectMapper);
		chain = mock(FilterChain.class);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private Jwt createJwt(Map<String, Object> cnfClaim) {
		Jwt.Builder builder = Jwt.withTokenValue("mock.access.token")
			.header("alg", "RS256")
			.subject("user-123")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(300))
			.claim("iss", "http://auth.localhost:8000/realms/shoplite");
		if (cnfClaim != null) {
			builder.claim("cnf", cnfClaim);
		}
		return builder.build();
	}

	private String createProof(String method, String path, String token) throws Exception {
		byte[] tokenHash = MessageDigest.getInstance("SHA-256")
			.digest(token.getBytes(StandardCharsets.US_ASCII));
		String ath = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenHash);

		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
			.type(new JOSEObjectType("dpop+jwt"))
			.jwk(clientKey.toPublicJWK())
			.build();

		JWTClaimsSet claims = new JWTClaimsSet.Builder()
			.jwtID(UUID.randomUUID().toString())
			.claim("htm", method)
			.claim("htu", path)
			.claim("ath", ath)
			.issueTime(Date.from(Instant.now()))
			.build();

		SignedJWT signed = new SignedJWT(header, claims);
		signed.sign(new ECDSASigner(clientKey));
		return signed.serialize();
	}

	@Test
	@DisplayName("Token with cnf.jkt and valid DPoP proof passes filter chain")
	void validDPoPRequest_proceeds() throws Exception {
		Jwt jwt = createJwt(Map.of("jkt", clientJkt));
		SecurityContextHolder.getContext().setAuthentication(
				new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
		request.addHeader("DPoP", createProof("POST", "/api/orders", "mock.access.token"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		verify(chain).doFilter(request, response);
	}

	@Test
	@DisplayName("Token with cnf.jkt but missing DPoP header returns 401")
	void missingDPoPHeader_returns401() throws Exception {
		Jwt jwt = createJwt(Map.of("jkt", clientJkt));
		SecurityContextHolder.getContext().setAuthentication(
				new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getHeader("WWW-Authenticate")).contains("invalid_dpop_proof");
		assertThat(response.getContentAsString()).contains("DPoP proof required");
		verifyNoInteractions(chain);
	}

	@Test
	@DisplayName("Token without cnf.jkt (legacy CLI / service token) proceeds through filter")
	void unconstrainedToken_proceeds() throws Exception {
		Jwt jwt = createJwt(null); // No cnf.jkt claim
		SecurityContextHolder.getContext().setAuthentication(
				new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		verify(chain).doFilter(request, response);
	}

	@Test
	@DisplayName("Unauthenticated public request passes filter untouched")
	void unauthenticatedRequest_proceeds() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
		verify(chain).doFilter(request, response);
	}

}
