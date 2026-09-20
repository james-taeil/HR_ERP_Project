package com.jamestaeil.hrerp.platform.api;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jamestaeil.hrerp.platform.authorization.AuthorizationDeniedException;
import com.jamestaeil.hrerp.platform.reference.ReferenceConflictException;
import com.jamestaeil.hrerp.platform.reference.ReferenceNotFoundException;

@RestControllerAdvice(assignableTypes = ReferenceController.class)
class ReferenceExceptionHandler {
	@ExceptionHandler(AuthorizationDeniedException.class)
	ResponseEntity<ApiError> forbidden() { return error(403, "FORBIDDEN", "Reference management is forbidden"); }
	@ExceptionHandler(ReferenceNotFoundException.class)
	ResponseEntity<ApiError> notFound() { return error(404, "REFERENCE_NOT_FOUND", "Reference value was not found"); }
	@ExceptionHandler(ReferenceConflictException.class)
	ResponseEntity<ApiError> conflict() { return error(409, "REFERENCE_CONFLICT", "Reference state conflicts with the request"); }
	@ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class})
	ResponseEntity<ApiError> invalid() { return error(400, "INVALID_REQUEST", "Reference request is invalid"); }
	private static ResponseEntity<ApiError> error(int status, String code, String message) {
		return ResponseEntity.status(status).body(new ApiError(code, message));
	}
	record ApiError(String code, String message) {}
}
