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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DPoPValidatorTests {

	private ECKey clientKey;
	private String clientJkt;
	private DPoPReplayCache replayCache;
	private DPoPValidator validator;

	@BeforeEach
	void setUp() throws Exception {
		clientKey = new ECKeyGenerator(Curve.P_256).keyID("test-key-1").generate();
		clientJkt = clientKey.toPublicJWK().computeThumbprint("SHA-256").toString();
		replayCache = new InMemoryDPoPReplayCache();
		validator = new DPoPValidator(replayCache, Duration.ofSeconds(60));
	}

	private String createDPoPProof(ECKey key, String htm, String htu, String accessToken, Instant iat, String jti)
			throws Exception {
		byte[] tokenHash = MessageDigest.getInstance("SHA-256")
			.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
		String ath = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenHash);

		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
			.type(new JOSEObjectType("dpop+jwt"))
			.jwk(key.toPublicJWK())
			.build();

		JWTClaimsSet claims = new JWTClaimsSet.Builder()
			.jwtID(jti != null ? jti : UUID.randomUUID().toString())
			.claim("htm", htm)
			.claim("htu", htu)
			.claim("ath", ath)
			.issueTime(Date.from(iat != null ? iat : Instant.now()))
			.build();

		SignedJWT signedJWT = new SignedJWT(header, claims);
		signedJWT.sign(new ECDSASigner(key));
		return signedJWT.serialize();
	}

	@Test
	@DisplayName("Valid DPoP proof matching cnf.jkt passes validation")
	void validDPoPProof_passes() throws Exception {
		String token = "mock.access.token";
		String proof = createDPoPProof(clientKey, "POST", "/api/orders", token, Instant.now(), UUID.randomUUID().toString());

		assertThatCode(() -> validator.validate(proof, token, "POST", "/api/orders", clientJkt))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("DPoP proof with absolute URL matches relative target request path")
	void absoluteUrlHtu_matchesPath() throws Exception {
		String token = "mock.access.token";
		String proof = createDPoPProof(clientKey, "POST", "http://shop.localhost:8000/api/orders", token, Instant.now(), UUID.randomUUID().toString());

		assertThatCode(() -> validator.validate(proof, token, "POST", "/api/orders", clientJkt))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Stolen token: proof signed by another key (thumbprint mismatch) is rejected")
	void thumbprintMismatch_isRejected() throws Exception {
		ECKey attackerKey = new ECKeyGenerator(Curve.P_256).generate();
		String token = "mock.access.token";
		String proof = createDPoPProof(attackerKey, "POST", "/api/orders", token, Instant.now(), UUID.randomUUID().toString());

		assertThatThrownBy(() -> validator.validate(proof, token, "POST", "/api/orders", clientJkt))
			.isInstanceOf(DPoPValidationException.class)
			.hasMessageContaining("thumbprint");
	}

	@Test
	@DisplayName("Tampered HTTP method (htm mismatch) is rejected")
	void methodMismatch_isRejected() throws Exception {
		String token = "mock.access.token";
		String proof = createDPoPProof(clientKey, "GET", "/api/orders", token, Instant.now(), UUID.randomUUID().toString());

		assertThatThrownBy(() -> validator.validate(proof, token, "POST", "/api/orders", clientJkt))
			.isInstanceOf(DPoPValidationException.class)
			.hasMessageContaining("htm");
	}

	@Test
	@DisplayName("Tampered HTTP URI path (htu mismatch) is rejected")
	void pathMismatch_isRejected() throws Exception {
		String token = "mock.access.token";
		String proof = createDPoPProof(clientKey, "POST", "/api/users", token, Instant.now(), UUID.randomUUID().toString());

		assertThatThrownBy(() -> validator.validate(proof, token, "POST", "/api/orders", clientJkt))
			.isInstanceOf(DPoPValidationException.class)
			.hasMessageContaining("htu");
	}

	@Test
	@DisplayName("Tampered access token hash (ath mismatch) is rejected")
	void tokenHashMismatch_isRejected() throws Exception {
		String token = "legitimate.token";
		String forgedToken = "stolen.token";
		String proof = createDPoPProof(clientKey, "POST", "/api/orders", token, Instant.now(), UUID.randomUUID().toString());

		assertThatThrownBy(() -> validator.validate(proof, forgedToken, "POST", "/api/orders", clientJkt))
			.isInstanceOf(DPoPValidationException.class)
			.hasMessageContaining("ath");
	}

	@Test
	@DisplayName("Expired timestamp (iat outside clock skew window) is rejected")
	void expiredIat_isRejected() throws Exception {
		String token = "mock.access.token";
		Instant expiredTime = Instant.now().minus(Duration.ofMinutes(5));
		String proof = createDPoPProof(clientKey, "POST", "/api/orders", token, expiredTime, UUID.randomUUID().toString());

		assertThatThrownBy(() -> validator.validate(proof, token, "POST", "/api/orders", clientJkt))
			.isInstanceOf(DPoPValidationException.class)
			.hasMessageContaining("iat");
	}

	@Test
	@DisplayName("Anti-replay: submitting same jti twice is rejected on second attempt")
	void replayAttack_isRejected() throws Exception {
		String token = "mock.access.token";
		String jti = UUID.randomUUID().toString();
		String proof = createDPoPProof(clientKey, "POST", "/api/orders", token, Instant.now(), jti);

		// First request passes
		assertThatCode(() -> validator.validate(proof, token, "POST", "/api/orders", clientJkt))
			.doesNotThrowAnyException();

		// Replay attempt with same proof and jti fails
		assertThatThrownBy(() -> validator.validate(proof, token, "POST", "/api/orders", clientJkt))
			.isInstanceOf(DPoPValidationException.class)
			.hasMessageContaining("Replay detected");
	}

}
