package com.shoplite.starter.security.dpop;

/**
 * Thrown when an RFC 9449 DPoP proof verification fails.
 */
public class DPoPValidationException extends RuntimeException {

	private final String errorCode;

	public DPoPValidationException(String errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public DPoPValidationException(String errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	public String getErrorCode() {
		return errorCode;
	}

}
