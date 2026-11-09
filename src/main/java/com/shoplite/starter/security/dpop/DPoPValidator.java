package com.shoplite.starter.security.dpop;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Validates RFC 9449 DPoP proofs against incoming HTTP requests and access tokens.
 */
public class DPoPValidator {

	private static final String TYPE_DPOP_JWT = "dpop+jwt";

	private final DPoPReplayCache replayCache;

	private final Duration maxClockSkew;

	public DPoPValidator(DPoPReplayCache replayCache, Duration maxClockSkew) {
		this.replayCache = replayCache != null ? replayCache : new InMemoryDPoPReplayCache();
		this.maxClockSkew = maxClockSkew != null ? maxClockSkew : Duration.ofSeconds(60);
	}

	/**
	 * Validates a DPoP proof header value.
	 *
	 * @param dpopProof the raw serialized JWT from the {@code DPoP} header
	 * @param accessToken the raw access token string presented with the request
	 * @param method the HTTP method of the request (e.g. GET, POST)
	 * @param requestUri the target request path (e.g. /api/orders)
	 * @param expectedJkt the expected SHA-256 JWK thumbprint from {@code cnf.jkt} in the access token
	 * @throws DPoPValidationException if any RFC 9449 rule is violated
	 */
	public void validate(String dpopProof, String accessToken, String method, String requestUri, String expectedJkt) {
		if (dpopProof == null || dpopProof.isBlank()) {
			throw new DPoPValidationException("invalid_dpop_proof", "DPoP proof header is missing or empty");
		}

		SignedJWT signedJWT;
		try {
			signedJWT = SignedJWT.parse(dpopProof);
		}
		catch (Exception ex) {
			throw new DPoPValidationException("invalid_dpop_proof", "Malformed DPoP proof JWT: " + ex.getMessage(), ex);
		}

		JWSHeader header = signedJWT.getHeader();

		// 1. Validate typ header
		JOSEObjectType type = header.getType();
		if (type == null || !TYPE_DPOP_JWT.equalsIgnoreCase(type.getType())) {
			throw new DPoPValidationException("invalid_dpop_proof", "DPoP proof typ must be 'dpop+jwt'");
		}

		// 2. Validate alg header
		JWSAlgorithm alg = header.getAlgorithm();
		if (alg == null || !JWSAlgorithm.ES256.equals(alg)) {
			throw new DPoPValidationException("invalid_dpop_proof", "DPoP proof algorithm must be ES256");
		}

		// 3. Extract and validate embedded public JWK
		JWK jwk = header.getJWK();
		if (!(jwk instanceof ECKey ecKey)) {
			throw new DPoPValidationException("invalid_dpop_proof", "DPoP proof header must contain an EC P-256 public JWK");
		}
		if (!Curve.P_256.equals(ecKey.getCurve())) {
			throw new DPoPValidationException("invalid_dpop_proof", "DPoP proof EC key curve must be P-256");
		}
		if (ecKey.isPrivate()) {
			throw new DPoPValidationException("invalid_dpop_proof", "DPoP proof JWK header must not contain private key material");
		}

		// 4. Validate thumbprint against token cnf.jkt
		String actualJkt;
		try {
			actualJkt = ecKey.toPublicJWK().computeThumbprint("SHA-256").toString();
		}
		catch (Exception ex) {
			throw new DPoPValidationException("invalid_dpop_proof", "Failed to compute JWK thumbprint: " + ex.getMessage(), ex);
		}

		if (expectedJkt != null && !expectedJkt.equals(actualJkt)) {
			throw new DPoPValidationException("invalid_dpop_proof",
					"DPoP proof JWK thumbprint (" + actualJkt + ") does not match token cnf.jkt (" + expectedJkt + ")");
		}

		// 5. Verify cryptographic signature using embedded public key
		try {
			ECDSAVerifier verifier = new ECDSAVerifier(ecKey.toECPublicKey());
			if (!signedJWT.verify(verifier)) {
				throw new DPoPValidationException("invalid_dpop_proof", "DPoP proof signature verification failed");
			}
		}
		catch (DPoPValidationException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new DPoPValidationException("invalid_dpop_proof", "Signature verification error: " + ex.getMessage(), ex);
		}

		// 6. Validate payload claims
		JWTClaimsSet claims;
		String jti;
		String htm;
		String htu;
		Date iat;
		String ath = null;
		try {
			claims = signedJWT.getJWTClaimsSet();
			jti = claims.getJWTID();
			htm = claims.getStringClaim("htm");
			htu = claims.getStringClaim("htu");
			iat = claims.getIssueTime();
			if (accessToken != null && !accessToken.isBlank()) {
				ath = claims.getStringClaim("ath");
			}
		}
		catch (Exception ex) {
			throw new DPoPValidationException("invalid_dpop_proof", "Failed to parse DPoP proof claims: " + ex.getMessage(), ex);
		}

		// 6a. jti claim (unique identifier)
		if (jti == null || jti.isBlank()) {
			throw new DPoPValidationException("invalid_dpop_proof", "DPoP proof jti claim is required");
		}

		// 6b. htm claim (HTTP method)
		if (htm == null || !htm.equalsIgnoreCase(method)) {
			throw new DPoPValidationException("invalid_dpop_proof",
					"DPoP proof htm claim (" + htm + ") does not match request method (" + method + ")");
		}

		// 6c. htu claim (HTTP target URI / path, RFC 9449 §4.2: must not contain query or fragment)
		if (htu == null || htu.isBlank()) {
			throw new DPoPValidationException("invalid_dpop_proof", "DPoP proof htu claim is required");
		}
		String htuPath = htu.contains("://") ? URI.create(htu).getPath() : htu;
		if (htuPath != null) {
			int queryIdx = htuPath.indexOf('?');
			if (queryIdx >= 0) {
				htuPath = htuPath.substring(0, queryIdx);
			}
			int fragIdx = htuPath.indexOf('#');
			if (fragIdx >= 0) {
				htuPath = htuPath.substring(0, fragIdx);
			}
		}
		if (!requestUri.equalsIgnoreCase(htuPath)) {
			throw new DPoPValidationException("invalid_dpop_proof",
					"DPoP proof htu path (" + htuPath + ") does not match request URI (" + requestUri + ")");
		}

		// 6d. iat claim (timestamp with clock skew)
		if (iat == null) {
			throw new DPoPValidationException("invalid_dpop_proof", "DPoP proof iat claim is required");
		}
		long skewSeconds = Math.abs(Instant.now().getEpochSecond() - iat.toInstant().getEpochSecond());
		if (skewSeconds > maxClockSkew.getSeconds()) {
			throw new DPoPValidationException("invalid_dpop_proof",
					"DPoP proof iat is outside the allowed clock skew window (" + skewSeconds + "s > " + maxClockSkew.getSeconds() + "s)");
		}

		// 6e. ath claim (access token hash)
		if (accessToken != null && !accessToken.isBlank()) {
			String expectedAth = computeAccessTokenHash(accessToken);
			if (ath == null || !ath.equals(expectedAth)) {
				throw new DPoPValidationException("invalid_dpop_proof",
						"DPoP proof ath claim does not match hash of the presented access token");
			}
		}

		// 7. Anti-replay protection
		Duration replayTtl = Duration.ofSeconds(maxClockSkew.getSeconds() * 2);
		boolean recorded = replayCache.recordIfAbsent(jti, replayTtl);
		if (!recorded) {
			throw new DPoPValidationException("use_dpop_nonce", "Replay detected: DPoP proof jti has already been used");
		}
	}

	public static String computeAccessTokenHash(String accessToken) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
				.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		}
		catch (Exception ex) {
			throw new IllegalStateException("SHA-256 message digest unavailable", ex);
		}
	}

}
