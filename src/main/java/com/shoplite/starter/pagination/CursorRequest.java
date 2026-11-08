package com.shoplite.starter.pagination;

/**
 * Standard request parameters for cursor-based keyset pagination.
 */
public record CursorRequest(String cursor, Integer limit) {

	public static final int DEFAULT_LIMIT = 20;
	public static final int MAX_LIMIT = 100;

	public int safeLimit() {
		if (limit == null || limit <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
	}

	public boolean hasCursor() {
		return cursor != null && !cursor.isBlank();
	}

}
