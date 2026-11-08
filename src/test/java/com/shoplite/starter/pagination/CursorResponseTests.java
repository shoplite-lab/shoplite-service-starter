package com.shoplite.starter.pagination;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CursorResponseTests {

	record SampleItem(Long id, Instant createdAt, String title) {
	}

	@Test
	void emptyRawItemsYieldsEmptyResponse() {
		CursorResponse<SampleItem> response = CursorResponse.of(
				List.of(),
				10,
				item -> new CursorCoordinates(item.createdAt(), item.id())
		);

		assertThat(response.items()).isEmpty();
		assertThat(response.nextCursor()).isNull();
		assertThat(response.hasMore()).isFalse();
	}

	@Test
	void itemsUnderLimitYieldsHasMoreFalseAndNoNextCursor() {
		Instant t1 = Instant.parse("2026-11-08T10:00:00Z");
		Instant t2 = Instant.parse("2026-11-08T09:00:00Z");
		List<SampleItem> items = List.of(
				new SampleItem(2L, t1, "Second"),
				new SampleItem(1L, t2, "First")
		);

		CursorResponse<SampleItem> response = CursorResponse.of(
				items,
				5,
				item -> new CursorCoordinates(item.createdAt(), item.id()),
				2L
		);

		assertThat(response.items()).hasSize(2);
		assertThat(response.hasMore()).isFalse();
		assertThat(response.nextCursor()).isNull();
		assertThat(response.totalCount()).isEqualTo(2L);
	}

	@Test
	void itemsExceedingLimitTrimsAndGeneratesNextCursor() {
		Instant t1 = Instant.parse("2026-11-08T10:00:00Z");
		Instant t2 = Instant.parse("2026-11-08T09:00:00Z");
		Instant t3 = Instant.parse("2026-11-08T08:00:00Z");

		List<SampleItem> rawItems = List.of(
				new SampleItem(3L, t1, "Third"),
				new SampleItem(2L, t2, "Second"),
				new SampleItem(1L, t3, "First (overflow)")
		);

		// Request limit = 2 with 3 items returned by query
		CursorResponse<SampleItem> response = CursorResponse.of(
				rawItems,
				2,
				item -> new CursorCoordinates(item.createdAt(), item.id())
		);

		assertThat(response.items()).hasSize(2);
		assertThat(response.items().get(0).title()).isEqualTo("Third");
		assertThat(response.items().get(1).title()).isEqualTo("Second");
		assertThat(response.hasMore()).isTrue();
		assertThat(response.nextCursor()).isNotNull();

		// Verify nextCursor points to the 2nd item (the last item on this page)
		CursorCoordinates decoded = CursorToken.decode(response.nextCursor());
		assertThat(decoded).isNotNull();
		assertThat(decoded.id()).isEqualTo(2L);
		assertThat(decoded.timestamp()).isEqualTo(t2);
	}

}
