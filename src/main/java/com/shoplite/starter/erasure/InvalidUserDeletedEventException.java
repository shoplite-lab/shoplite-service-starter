package com.shoplite.starter.erasure;

/**
 * The {@code identity.user-deleted} event can never be processed (unparseable JSON or missing
 * {@code payload.userId}): not retryable, sent straight to the consumer's dead-letter topic.
 */
public class InvalidUserDeletedEventException extends RuntimeException {

	public InvalidUserDeletedEventException(String message) {
		super(message);
	}

	public InvalidUserDeletedEventException(String message, Throwable cause) {
		super(message, cause);
	}

}
