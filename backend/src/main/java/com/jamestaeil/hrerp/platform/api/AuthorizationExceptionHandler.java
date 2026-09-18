package com.jamestaeil.hrerp.platform.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jamestaeil.hrerp.platform.authorization.AuthorizationConflictException;
import com.jamestaeil.hrerp.platform.authorization.AuthorizationDeniedException;
import com.jamestaeil.hrerp.platform.authorization.AuthorizationResourceNotFoundException;

@RestControllerAdvice(assignableTypes = AuthorizationController.class)
class AuthorizationExceptionHandler {
	@ExceptionHandler(AuthorizationDeniedException.class)
	ResponseEntity<ApiError> forbidden() {
		return error(403, "FORBIDDEN", "Authorization management is forbidden");
	}

	@ExceptionHandler(AuthorizationResourceNotFoundException.class)
	ResponseEntity<ApiError> notFound() {
		return error(404, "AUTHORIZATION_RESOURCE_NOT_FOUND", "Authorization resource was not found");
	}

	@ExceptionHandler({AuthorizationConflictException.class, DataIntegrityViolationException.class})
	ResponseEntity<ApiError> conflict() {
		return error(409, "AUTHORIZATION_CONFLICT", "Authorization state conflicts with the request");
	}

	@ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class})
	ResponseEntity<ApiError> invalid() {
		return error(400, "INVALID_REQUEST", "Authorization request is invalid");
	}

	private static ResponseEntity<ApiError> error(int status, String code, String message) {
		return ResponseEntity.status(status).body(new ApiError(code, message));
	}

	record ApiError(String code, String message) {}
}
