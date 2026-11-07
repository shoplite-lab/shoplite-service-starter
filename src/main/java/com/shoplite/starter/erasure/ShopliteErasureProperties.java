package com.shoplite.starter.erasure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of the {@code identity.user-deleted} tombstone and consumer retry/DLT (ADR 0002).
 */
@ConfigurationProperties("shoplite.erasure")
public class ShopliteErasureProperties {

	/**
	 * Whether this service maintains an {@code erased_subjects} tombstone table and registers the
	 * erasure listener container factory.
	 */
	private boolean enabled = false;

	private Retry retry = new Retry();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Retry getRetry() {
		return retry;
	}

	public void setRetry(Retry retry) {
		this.retry = retry == null ? new Retry() : retry;
	}

	public static class Retry {

		private Duration initialInterval = Duration.ofSeconds(1);

		private double multiplier = 2.0;

		private Duration maxInterval = Duration.ofSeconds(60);

		private Duration maxElapsedTime = Duration.ofHours(1);

		public Duration getInitialInterval() {
			return initialInterval;
		}

		public void setInitialInterval(Duration initialInterval) {
			this.initialInterval = initialInterval;
		}

		public double getMultiplier() {
			return multiplier;
		}

		public void setMultiplier(double multiplier) {
			this.multiplier = multiplier;
		}

		public Duration getMaxInterval() {
			return maxInterval;
		}

		public void setMaxInterval(Duration maxInterval) {
			this.maxInterval = maxInterval;
		}

		public Duration getMaxElapsedTime() {
			return maxElapsedTime;
		}

		public void setMaxElapsedTime(Duration maxElapsedTime) {
			this.maxElapsedTime = maxElapsedTime;
		}

	}

}
