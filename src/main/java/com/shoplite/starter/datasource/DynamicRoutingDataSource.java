package com.shoplite.starter.datasource;

import java.time.Duration;
import java.util.Objects;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link AbstractRoutingDataSource} that directs read-write transactions (and read-only
 * transactions within the caller's {@link StickyWriteContext} window) to
 * {@link DataSourceRoute#WRITER}, and ordinary read-only transactions to
 * {@link DataSourceRoute#READER}.
 */
public class DynamicRoutingDataSource extends AbstractRoutingDataSource {

	private final StickyWriteContext stickyWriteContext;

	private final Duration stickyDuration;

	public DynamicRoutingDataSource(StickyWriteContext stickyWriteContext, Duration stickyDuration) {
		this.stickyWriteContext = Objects.requireNonNull(stickyWriteContext, "stickyWriteContext must not be null");
		this.stickyDuration = stickyDuration != null ? stickyDuration : Duration.ofSeconds(5);
	}

	@Override
	protected Object determineCurrentLookupKey() {
		return resolveRoute();
	}

	/**
	 * Resolves the active {@link DataSourceRoute} for the current transaction context.
	 */
	public DataSourceRoute resolveRoute() {
		boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
		if (!readOnly) {
			// Record sticky write only for actual active transactions so startup metadata/health
			// probes do not pin subsequent reads to the writer.
			if (TransactionSynchronizationManager.isActualTransactionActive()) {
				stickyWriteContext.markWrite();
			}
			return DataSourceRoute.WRITER;
		}
		if (stickyWriteContext.shouldForceWriter(stickyDuration)) {
			return DataSourceRoute.WRITER;
		}
		return DataSourceRoute.READER;
	}

}
