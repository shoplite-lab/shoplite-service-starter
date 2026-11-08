package com.shoplite.starter.pagination;

import java.time.Instant;

/**
 * Natural composite key coordinates for keyset pagination.
 */
public record CursorCoordinates(Instant timestamp, Long id) {

	public CursorCoordinates {
		if (id == null && timestamp == null) {
			throw new IllegalArgumentException("At least one coordinate (id or timestamp) must be provided");
		}
	}

}
