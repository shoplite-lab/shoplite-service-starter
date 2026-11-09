package com.shoplite.starter.contract;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates domain event JSON payloads against canonical JSON Schemas (Draft 2020-12).
 *
 * <p>Schemas are cached in memory for sub-millisecond assertion execution in producer and consumer
 * contract tests.
 */
public final class EventSchemaValidator {

	private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

	private static final Map<String, JsonSchema> SCHEMA_CACHE = new ConcurrentHashMap<>();

	private EventSchemaValidator() {
	}

	public record ValidationResult(boolean isValid, List<String> errors) {

		public static ValidationResult success() {
			return new ValidationResult(true, List.of());
		}

		public static ValidationResult failure(List<String> errors) {
			return new ValidationResult(false, List.copyOf(errors));
		}
	}

	/**
	 * Validates an event JSON string against the canonical schema for {@code eventType} and
	 * {@code version}.
	 *
	 * @param eventType domain event type (e.g. {@code order.created}, {@code payment.succeeded})
	 * @param version major schema version (e.g. 1)
	 * @param jsonString raw JSON string of the event
	 * @return {@link ValidationResult} with success flag and error details
	 */
	public static ValidationResult validate(String eventType, int version, String jsonString) {
		String resourcePath = resolveSchemaPath(eventType, version);
		return validateAgainstResource(resourcePath, jsonString);
	}

	/**
	 * Asserts that the given event JSON string satisfies the canonical schema.
	 *
	 * @throws AssertionError if schema validation fails
	 */
	public static void assertValid(String eventType, int version, String jsonString) {
		ValidationResult result = validate(eventType, version, jsonString);
		if (!result.isValid()) {
			throw new AssertionError(String.format("Event validation failed for %s v%d: %s",
					eventType, version, String.join("; ", result.errors())));
		}
	}

	/**
	 * Validates raw JSON string against an arbitrary schema resource on the classpath.
	 */
	public static ValidationResult validateAgainstResource(String resourcePath, String jsonString) {
		JsonSchema schema = SCHEMA_CACHE.computeIfAbsent(resourcePath, path -> {
			String normalizedPath = path.startsWith("/") ? path : "/" + path;
			InputStream in = EventSchemaValidator.class.getResourceAsStream(normalizedPath);
			if (in == null) {
				throw new IllegalArgumentException("Canonical event schema resource not found: " + normalizedPath);
			}
			return FACTORY.getSchema(in);
		});

		Set<ValidationMessage> messages = schema.validate(jsonString, InputFormat.JSON);
		if (messages.isEmpty()) {
			return ValidationResult.success();
		}
		List<String> errors = messages.stream().map(ValidationMessage::getMessage).toList();
		return ValidationResult.failure(errors);
	}

	private static String resolveSchemaPath(String eventType, int version) {
		String normalized = eventType.replace('.', '-').replace('_', '-');
		if ("payment-expired".equals(normalized)) {
			normalized = "payment-failed";
		}
		return "/contracts/events/" + normalized + ".v" + version + ".json";
	}

}
