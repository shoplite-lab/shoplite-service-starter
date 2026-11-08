package com.shoplite.starter.web;

import com.shoplite.starter.erasure.AccountErasedException;
import com.shoplite.starter.pagination.InvalidCursorException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Standard RFC 9457 ProblemDetail exception handler across all ShopLite microservices.
 * Enriches problem responses with UTC timestamp, traceId, and structured business error codes.
 */
@RestControllerAdvice
public class ShopliteProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ShopliteProblemDetailsExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {

		ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, "Validation failed for one or more fields");
		detail.setTitle("Validation Failed");
		detail.setType(URI.create("https://shoplite.internal/errors/validation-failed"));

		Map<String, String> fieldErrors = new HashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		detail.setProperty("errors", fieldErrors);
		enrichProblemDetail(detail, "VALIDATION_FAILED", request);

		return handleExceptionInternal(ex, detail, headers, status, request);
	}

	@ExceptionHandler(InvalidCursorException.class)
	public ResponseEntity<Object> handleInvalidCursor(InvalidCursorException ex, WebRequest request) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		detail.setTitle("Invalid Cursor");
		detail.setType(URI.create("https://shoplite.internal/errors/invalid-cursor"));
		enrichProblemDetail(detail, "INVALID_CURSOR", request);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
	}

	@ExceptionHandler(AccountErasedException.class)
	public ResponseEntity<Object> handleAccountErased(AccountErasedException ex, WebRequest request) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
		detail.setTitle("Account Erased");
		detail.setType(URI.create("https://shoplite.internal/errors/account-erased"));
		enrichProblemDetail(detail, "ACCOUNT_ERASED", request);
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(detail);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied: insufficient permissions");
		detail.setTitle("Forbidden");
		detail.setType(URI.create("https://shoplite.internal/errors/forbidden"));
		enrichProblemDetail(detail, "FORBIDDEN", request);
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(detail);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Object> handleResponseStatus(ResponseStatusException ex, WebRequest request) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason() != null ? ex.getReason() : "");
		String code = "HTTP_" + ex.getStatusCode().value();
		enrichProblemDetail(detail, code, request);
		return ResponseEntity.status(ex.getStatusCode()).body(detail);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		detail.setTitle("Bad Request");
		detail.setType(URI.create("https://shoplite.internal/errors/bad-request"));
		enrichProblemDetail(detail, "BAD_REQUEST", request);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleAllUncaughtException(Exception ex, WebRequest request) {
		String traceId = resolveTraceId();
		log.error("Unhandled exception processing request [traceId={}]: {}", traceId, ex.getMessage(), ex);

		ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal error occurred");
		detail.setTitle("Internal Server Error");
		detail.setType(URI.create("https://shoplite.internal/errors/internal-error"));
		enrichProblemDetail(detail, "INTERNAL_SERVER_ERROR", request);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(detail);
	}

	public static void enrichProblemDetail(ProblemDetail detail, String errorCode, WebRequest request) {
		if (detail == null) {
			return;
		}
		detail.setProperty("timestamp", Instant.now().toString());
		detail.setProperty("code", errorCode);
		String traceId = resolveTraceId();
		if (traceId != null && !traceId.isBlank()) {
			detail.setProperty("traceId", traceId);
		}
		if (request instanceof ServletWebRequest servletRequest) {
			detail.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
		}
	}

	private static String resolveTraceId() {
		String traceId = MDC.get("traceId");
		if (traceId == null || traceId.isBlank()) {
			traceId = MDC.get("trace_id");
		}
		return traceId;
	}

}
