package com.shoplite.starter.erasure;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * The caller's account was deleted (tombstone): 403 with a RFC 9457 {@link ProblemDetail}, matching
 * the response returned by {@link ErasedSubjectFilter}.
 */
public class AccountErasedException extends ErrorResponseException {

	public static final String DETAIL = ErasedSubjects.DELETED_DETAIL;

	public AccountErasedException() {
		super(HttpStatus.FORBIDDEN, ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, DETAIL), null);
	}

}
