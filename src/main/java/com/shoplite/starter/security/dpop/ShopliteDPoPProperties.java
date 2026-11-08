package com.shoplite.starter.security.dpop;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for RFC 9449 DPoP sender-constrained tokens.
 */
@ConfigurationProperties(prefix = "shoplite.security.dpop")
public class ShopliteDPoPProperties {

	/**
	 * Whether DPoP verification is enabled. Defaults to {@code true}.
	 */
	private boolean enabled = true;

	/**
	 * Maximum allowed clock skew between client {@code iat} and server time. Defaults to 60 seconds.
	 */
	private Duration clockSkew = Duration.ofSeconds(60);

	/**
	 * If {@code true}, every authenticated request must provide a DPoP-bound token and proof.
	 * If {@code false} (default), unconstrained tokens without {@code cnf.jkt} (such as CLI or internal
	 * service tokens) are permitted for backward compatibility.
	 */
	private boolean strict = false;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Duration getClockSkew() {
		return clockSkew;
	}

	public void setClockSkew(Duration clockSkew) {
		this.clockSkew = clockSkew;
	}

	public boolean isStrict() {
		return strict;
	}

	public void setStrict(boolean strict) {
		this.strict = strict;
	}

}
