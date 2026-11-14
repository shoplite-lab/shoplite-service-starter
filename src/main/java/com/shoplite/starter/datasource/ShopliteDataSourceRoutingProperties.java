package com.shoplite.starter.datasource;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Read/Write PostgreSQL connection splitting.
 */
@ConfigurationProperties(prefix = "shoplite.datasource.routing")
public class ShopliteDataSourceRoutingProperties {

	/** Enable primary/replica datasource routing. */
	private boolean enabled = false;

	/** Primary (read-write) JDBC URL. */
	private String writerUrl;

	/** Read-replica JDBC URL (falls back to writerUrl when omitted). */
	private String readerUrl;

	private String username;

	private String password;

	private String driverClassName = "org.postgresql.Driver";

	/** Duration after a write during which the same actor stays pinned to WRITER. */
	private Duration stickyDuration = Duration.ofSeconds(5);

	private int writerMaxPoolSize = 10;

	private int readerMaxPoolSize = 10;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getWriterUrl() {
		return writerUrl;
	}

	public void setWriterUrl(String writerUrl) {
		this.writerUrl = writerUrl;
	}

	public String getReaderUrl() {
		return readerUrl;
	}

	public void setReaderUrl(String readerUrl) {
		this.readerUrl = readerUrl;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getDriverClassName() {
		return driverClassName;
	}

	public void setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
	}

	public Duration getStickyDuration() {
		return stickyDuration;
	}

	public void setStickyDuration(Duration stickyDuration) {
		this.stickyDuration = stickyDuration;
	}

	public int getWriterMaxPoolSize() {
		return writerMaxPoolSize;
	}

	public void setWriterMaxPoolSize(int writerMaxPoolSize) {
		this.writerMaxPoolSize = writerMaxPoolSize;
	}

	public int getReaderMaxPoolSize() {
		return readerMaxPoolSize;
	}

	public void setReaderMaxPoolSize(int readerMaxPoolSize) {
		this.readerMaxPoolSize = readerMaxPoolSize;
	}

}
