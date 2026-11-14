package com.shoplite.starter.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DynamicRoutingDataSourceTests {

	@AfterEach
	void cleanup() {
		MDC.clear();
		TransactionSynchronizationManager.setActualTransactionActive(false);
		TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
	}

	@Test
	void routesReadOnlyToReaderAndReadWriteToWriter() {
		MutableClock clock = new MutableClock(Instant.parse("2026-11-14T00:00:00Z"));
		StickyWriteContext sticky = new StickyWriteContext(clock);
		DynamicRoutingDataSource routing = new DynamicRoutingDataSource(sticky, Duration.ofSeconds(5));

		MDC.put("sub", "user-1");
		TransactionSynchronizationManager.setActualTransactionActive(true);
		TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
		assertThat(routing.resolveRoute()).isEqualTo(DataSourceRoute.READER);

		TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
		assertThat(routing.resolveRoute()).isEqualTo(DataSourceRoute.WRITER);
	}

	@Test
	void forcesWriterForSameActorDuringStickyWindowAndFallsBackToReaderAfterExpiry() {
		MutableClock clock = new MutableClock(Instant.parse("2026-11-14T00:00:00Z"));
		StickyWriteContext sticky = new StickyWriteContext(clock);
		DynamicRoutingDataSource routing = new DynamicRoutingDataSource(sticky, Duration.ofSeconds(5));

		MDC.put("sub", "user-1");
		TransactionSynchronizationManager.setActualTransactionActive(true);
		TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
		assertThat(routing.resolveRoute()).isEqualTo(DataSourceRoute.WRITER);

		// Within 5s sticky window, even readOnly=true routes to WRITER for user-1
		clock.advance(Duration.ofSeconds(2));
		TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
		assertThat(routing.resolveRoute()).isEqualTo(DataSourceRoute.WRITER);

		// Another actor is unaffected and continues hitting READER
		MDC.put("sub", "user-2");
		assertThat(routing.resolveRoute()).isEqualTo(DataSourceRoute.READER);

		// Once 5s expires, user-1 falls back to READER
		MDC.put("sub", "user-1");
		clock.advance(Duration.ofSeconds(4));
		assertThat(routing.resolveRoute()).isEqualTo(DataSourceRoute.READER);
	}

	private static final class MutableClock extends Clock {

		private final AtomicReference<Instant> current;

		MutableClock(Instant initial) {
			this.current = new AtomicReference<>(initial);
		}

		void advance(Duration delta) {
			current.updateAndGet(now -> now.plus(delta));
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return current.get();
		}

	}

}
