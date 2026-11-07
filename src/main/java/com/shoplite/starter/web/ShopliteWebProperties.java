package com.shoplite.starter.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for ShopLite's structured HTTP access log filter.
 */
@ConfigurationProperties("shoplite.web.access-log")
public class ShopliteWebProperties {

	/** Whether to register {@link AccessLogFilter}. */
	private boolean enabled = true;

	/** Skip {@code /actuator/**} probe/metrics scrapes so Loki is not flooded. */
	private boolean ignoreActuator = true;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isIgnoreActuator() {
		return ignoreActuator;
	}

	public void setIgnoreActuator(boolean ignoreActuator) {
		this.ignoreActuator = ignoreActuator;
	}

}
