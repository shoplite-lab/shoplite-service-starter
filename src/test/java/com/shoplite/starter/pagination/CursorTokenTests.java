package com.shoplite.starter.pagination;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorTokenTests {

	@Test
	void encodesAndDecodesTimestampAndId() {
		Instant now = Instant.parse("2026-11-08T12:00:00.123456Z");
		Long id = 42L;

		String token = CursorToken.encode(now, id);
		assertThat(token).isNotBlank();

		CursorCoordinates decoded = CursorToken.decode(token);
		assertThat(decoded).isNotNull();
		assertThat(decoded.timestamp()).isEqualTo(now);
		assertThat(decoded.id()).isEqualTo(42L);
	}

	@Test
	void encodesAndDecodesIdOnly() {
		String token = CursorToken.encodeId(999L);
		assertThat(token).isNotBlank();

		Long decodedId = CursorToken.decodeId(token);
		assertThat(decodedId).isEqualTo(999L);

		CursorCoordinates decoded = CursorToken.decode(token);
		assertThat(decoded).isNotNull();
		assertThat(decoded.id()).isEqualTo(999L);
		assertThat(decoded.timestamp()).isNull();
	}

	@Test
	void decodeNullOrBlankReturnsNull() {
		assertThat(CursorToken.decode(null)).isNull();
		assertThat(CursorToken.decode("")).isNull();
		assertThat(CursorToken.decode("   ")).isNull();
		assertThat(CursorToken.decodeId(null)).isNull();
	}

	@Test
	void decodeInvalidBase64ThrowsInvalidCursorException() {
		assertThatThrownBy(() -> CursorToken.decode("!!!not-base64-content***"))
				.isInstanceOf(InvalidCursorException.class)
				.hasMessageContaining("Invalid pagination cursor token");
	}

	@Test
	void decodeNonJsonThrowsInvalidCursorException() {
		String invalidJson = java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString("plain text string".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		assertThatThrownBy(() -> CursorToken.decode(invalidJson))
				.isInstanceOf(InvalidCursorException.class);
	}

	@Test
	void decodeEmptyJsonObjectThrowsInvalidCursorException() {
		// Base64Url for "{}"
		String emptyJsonToken = "e30";
		assertThatThrownBy(() -> CursorToken.decode(emptyJsonToken))
				.isInstanceOf(InvalidCursorException.class)
				.hasMessageContaining("missing both timestamp and id");
	}

	@Test
	void encodeNullCoordinatesReturnsNull() {
		assertThat(CursorToken.encode(null, null)).isNull();
	}

}
