package com.shoplite.starter.pagination;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Standard envelope for cursor-paginated API responses.
 *
 * @param <T> Item entity or view DTO
 * @param items List of items on the current page
 * @param nextCursor Opaque cursor token for the next page, or {@code null} if no further items exist
 * @param hasMore {@code true} if there are more items to fetch after this page
 * @param totalCount Optional total count (omitted or null in high-volume infinite scroll to avoid costly COUNT(*))
 */
public record CursorResponse<T>(
		List<T> items,
		String nextCursor,
		boolean hasMore,
		Long totalCount
) {

	public CursorResponse {
		items = items != null ? Collections.unmodifiableList(items) : Collections.emptyList();
	}

	public static <T> CursorResponse<T> of(
			List<T> rawItems,
			int requestedLimit,
			Function<T, CursorCoordinates> coordinatesExtractor
	) {
		return of(rawItems, requestedLimit, coordinatesExtractor, null);
	}

	public static <T> CursorResponse<T> of(
			List<T> rawItems,
			int requestedLimit,
			Function<T, CursorCoordinates> coordinatesExtractor,
			Long totalCount
	) {
		if (rawItems == null || rawItems.isEmpty()) {
			return new CursorResponse<>(Collections.emptyList(), null, false, totalCount);
		}
		int limit = requestedLimit > 0 ? requestedLimit : CursorRequest.DEFAULT_LIMIT;
		boolean hasMore = rawItems.size() > limit;
		List<T> pageItems = hasMore ? rawItems.subList(0, limit) : rawItems;

		String nextCursor = null;
		if (hasMore && !pageItems.isEmpty()) {
			T lastItem = pageItems.get(pageItems.size() - 1);
			CursorCoordinates coords = coordinatesExtractor.apply(lastItem);
			if (coords != null) {
				nextCursor = CursorToken.encode(coords.timestamp(), coords.id());
			}
		}

		return new CursorResponse<>(pageItems, nextCursor, hasMore, totalCount);
	}

	public static <T> CursorResponse<T> empty() {
		return new CursorResponse<>(Collections.emptyList(), null, false, 0L);
	}

}
