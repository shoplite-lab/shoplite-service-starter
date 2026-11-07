package com.shoplite.starter.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings for ShopLite resource servers (ADR 0001 A3, ADR 0003).
 */
@ConfigurationProperties("shoplite.security")
public class ShopliteSecurityProperties {

	/**
	 * Enabled by default; set {@code false} to disable the starter's default {@code SecurityFilterChain}.
	 */
	private boolean enabled = true;

	/**
	 * Optional required audiences ({@code aud} claim). When non-empty, a token must carry at least
	 * one of these values or it is rejected. Empty by default for expand/contract rollout.
	 */
	private List<String> audiences = List.of();

	/**
	 * JWT claim used as the principal name of a staff token. The opaque {@code sub} by default, so
	 * logs and audit rows never carry a username. A service that logged {@code preferred_username}
	 * before adopting the starter may set that to keep its behaviour until it moves to {@code sub}.
	 */
	private String staffPrincipalClaim = "sub";

	public String getStaffPrincipalClaim() {
		return staffPrincipalClaim;
	}

	public void setStaffPrincipalClaim(String staffPrincipalClaim) {
		this.staffPrincipalClaim = staffPrincipalClaim;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public List<String> getAudiences() {
		return audiences;
	}

	public void setAudiences(List<String> audiences) {
		this.audiences = audiences == null ? List.of() : List.copyOf(audiences);
	}

}
