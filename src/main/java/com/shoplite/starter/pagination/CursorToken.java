package com.shoplite.starter.pagination;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Encodes and decodes opaque Base64Url cursor tokens for keyset pagination.
 * Clients treat this token as opaque; internal schema can evolve safely.
 */
public final class CursorToken {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private CursorToken() {
	}

	public static String encode(Instant timestamp, Long id) {
		if (timestamp == null && id == null) {
			return null;
		}
		try {
			ObjectNode node = MAPPER.createObjectNode();
			if (timestamp != null) {
				node.put("t", timestamp.toString());
			}
			if (id != null) {
				node.put("id", id);
			}
			String json = MAPPER.writeValueAsString(node);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to encode cursor token", e);
		}
	}

	public static CursorCoordinates decode(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			byte[] decodedBytes = Base64.getUrlDecoder().decode(cursor.trim());
			var node = MAPPER.readTree(decodedBytes);
			if (!node.isObject()) {
				throw new InvalidCursorException("Malformed cursor token: JSON object expected");
			}
			Instant timestamp = null;
			if (node.hasNonNull("t")) {
				timestamp = Instant.parse(node.get("t").asText());
			}
			Long id = null;
			if (node.hasNonNull("id")) {
				id = node.get("id").asLong();
			}
			if (timestamp == null && id == null) {
				throw new InvalidCursorException("Malformed cursor token: missing both timestamp and id");
			}
			return new CursorCoordinates(timestamp, id);
		}
		catch (IllegalArgumentException | IOException e) {
			throw new InvalidCursorException("Invalid pagination cursor token: " + e.getMessage(), e);
		}
	}

	public static String encodeId(Long id) {
		return encode(null, id);
	}

	public static Long decodeId(String cursor) {
		CursorCoordinates coords = decode(cursor);
		return coords != null ? coords.id() : null;
	}

}
