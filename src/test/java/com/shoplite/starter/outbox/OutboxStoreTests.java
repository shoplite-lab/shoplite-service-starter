package com.shoplite.starter.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;

class OutboxStoreTests {

	private JdbcTemplate jdbcTemplate;
	private OutboxStore store;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(JdbcTemplate.class);
		store = new OutboxStore(jdbcTemplate);
		MDC.clear();
	}

	@AfterEach
	void tearDown() {
		MDC.clear();
	}

	@Test
	void enqueue_withNumericString_parsesToLong() {
		when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

		store.enqueue("evt-1", "12345", "test.event", "{}");

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(jdbcTemplate).update(anyString(), eq("evt-1"), captor.capture(), eq("test.event"), eq("{}"), any());
		assertThat(captor.getValue()).isEqualTo(12345L);
	}

	@Test
	void enqueue_withNonNumericString_hashesSafelyToPositiveLong() {
		when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

		store.enqueue("evt-2", "devops1", "test.event", "{}");

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(jdbcTemplate).update(anyString(), eq("evt-2"), captor.capture(), eq("test.event"), eq("{}"), any());
		assertThat(captor.getValue()).isInstanceOf(Long.class);
		assertThat((Long) captor.getValue()).isGreaterThanOrEqualTo(0L);
	}

	@Test
	void enqueue_capturesTraceparentFromMdc() {
		MDC.put("trace_id", "4bf92f3577b34da6a3ce929d0e0e4736");
		MDC.put("span_id", "00f067aa0ba902b7");

		when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any())).thenReturn(1);

		store.enqueue("evt-3", 100L, "test.event", "{}");

		verify(jdbcTemplate).update(anyString(), eq("evt-3"), eq(100L), eq("test.event"), eq("{}"),
				eq("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));
	}

	@Test
	void claim_includesAggregateOrderingConditionInSql() {
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

		store.claim(10, 5);

		verify(jdbcTemplate).query(sqlCaptor.capture(), any(org.springframework.jdbc.core.RowMapper.class), eq(5), eq(10));
		assertThat(sqlCaptor.getValue())
				.contains("NOT EXISTS")
				.contains("earlier.aggregate_id = o.aggregate_id");
	}

}
