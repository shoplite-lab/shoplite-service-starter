package com.shoplite.starter.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the transactional outbox relay.
 */
@ConfigurationProperties("shoplite.outbox")
public class OutboxProperties {

	private boolean enabled = false;

	private Duration pollInterval = Duration.ofSeconds(1);

	private Duration initialDelay = Duration.ofSeconds(1);

	private int batchSize = 20;

	private int maxAttempts = 100;

	private Duration staleAfter = Duration.ofMinutes(5);

	private Duration reapInterval = Duration.ofSeconds(30);

	private Duration retention = Duration.ofDays(7);

	private String cleanupCron = "0 30 3 * * *";

	private Duration metricsInterval = Duration.ofSeconds(15);

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Duration getPollInterval() {
		return pollInterval;
	}

	public void setPollInterval(Duration pollInterval) {
		this.pollInterval = pollInterval;
	}

	public Duration getInitialDelay() {
		return initialDelay;
	}

	public void setInitialDelay(Duration initialDelay) {
		this.initialDelay = initialDelay;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public Duration getStaleAfter() {
		return staleAfter;
	}

	public void setStaleAfter(Duration staleAfter) {
		this.staleAfter = staleAfter;
	}

	public Duration getReapInterval() {
		return reapInterval;
	}

	public void setReapInterval(Duration reapInterval) {
		this.reapInterval = reapInterval;
	}

	public Duration getRetention() {
		return retention;
	}

	public void setRetention(Duration retention) {
		this.retention = retention;
	}

	public String getCleanupCron() {
		return cleanupCron;
	}

	public void setCleanupCron(String cleanupCron) {
		this.cleanupCron = cleanupCron;
	}

	public Duration getMetricsInterval() {
		return metricsInterval;
	}

	public void setMetricsInterval(Duration metricsInterval) {
		this.metricsInterval = metricsInterval;
	}

}
