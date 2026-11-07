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
