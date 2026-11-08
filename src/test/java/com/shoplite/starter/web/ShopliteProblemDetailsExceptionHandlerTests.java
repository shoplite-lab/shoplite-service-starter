package com.shoplite.starter.web;

import com.shoplite.starter.erasure.AccountErasedException;
import com.shoplite.starter.pagination.InvalidCursorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class ShopliteProblemDetailsExceptionHandlerTests {

	private ShopliteProblemDetailsExceptionHandler handler;
	private ServletWebRequest webRequest;

	@BeforeEach
	void setUp() {
		handler = new ShopliteProblemDetailsExceptionHandler();
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
		webRequest = new ServletWebRequest(request);
		MDC.put("traceId", "test-trace-12345");
	}

	@AfterEach
	void tearDown() {
		MDC.clear();
	}

	@Test
	void handlesInvalidCursorException() {
		InvalidCursorException ex = new InvalidCursorException("Malformed cursor token");
		ResponseEntity<Object> response = handler.handleInvalidCursor(ex, webRequest);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		ProblemDetail detail = (ProblemDetail) response.getBody();
		assertThat(detail).isNotNull();
		assertThat(detail.getTitle()).isEqualTo("Invalid Cursor");
		assertThat(detail.getDetail()).isEqualTo("Malformed cursor token");
		assertThat(detail.getProperties())
				.containsEntry("code", "INVALID_CURSOR")
				.containsEntry("traceId", "test-trace-12345");
		assertThat(detail.getProperties().get("timestamp")).isNotNull();
	}

	@Test
	void handlesAccountErasedException() {
		AccountErasedException ex = new AccountErasedException();
		ResponseEntity<Object> response = handler.handleAccountErased(ex, webRequest);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		ProblemDetail detail = (ProblemDetail) response.getBody();
		assertThat(detail).isNotNull();
		assertThat(detail.getTitle()).isEqualTo("Account Erased");
		assertThat(detail.getProperties()).containsEntry("code", "ACCOUNT_ERASED");
	}

	@Test
	void handlesAccessDeniedException() {
		AccessDeniedException ex = new AccessDeniedException("Access denied");
		ResponseEntity<Object> response = handler.handleAccessDenied(ex, webRequest);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		ProblemDetail detail = (ProblemDetail) response.getBody();
		assertThat(detail).isNotNull();
		assertThat(detail.getTitle()).isEqualTo("Forbidden");
		assertThat(detail.getProperties()).containsEntry("code", "FORBIDDEN");
	}

	@Test
	void handlesResponseStatusException() {
		ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
		ResponseEntity<Object> response = handler.handleResponseStatus(ex, webRequest);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		ProblemDetail detail = (ProblemDetail) response.getBody();
		assertThat(detail).isNotNull();
		assertThat(detail.getProperties()).containsEntry("code", "HTTP_404");
	}

	@Test
	void handlesGenericExceptionHidingInternalStack() {
		RuntimeException ex = new RuntimeException("Sensitive database credentials error");
		ResponseEntity<Object> response = handler.handleAllUncaughtException(ex, webRequest);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		ProblemDetail detail = (ProblemDetail) response.getBody();
		assertThat(detail).isNotNull();
		assertThat(detail.getTitle()).isEqualTo("Internal Server Error");
		assertThat(detail.getDetail()).doesNotContain("database credentials");
		assertThat(detail.getProperties()).containsEntry("code", "INTERNAL_SERVER_ERROR");
	}

}
