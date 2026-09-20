package com.jamestaeil.hrerp.platform.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jamestaeil.hrerp.platform.authorization.AuthorizationDeniedException;
import com.jamestaeil.hrerp.platform.organization.OrganizationConflictException;
import com.jamestaeil.hrerp.platform.organization.OrganizationNotFoundException;

@RestControllerAdvice(assignableTypes = OrganizationController.class)
class OrganizationExceptionHandler {
	@ExceptionHandler(AuthorizationDeniedException.class)
	ResponseEntity<ApiError> forbidden() { return error(403, "FORBIDDEN", "Organization management is forbidden"); }
	@ExceptionHandler(OrganizationNotFoundException.class)
	ResponseEntity<ApiError> notFound() { return error(404, "ORGANIZATION_NOT_FOUND", "Organization was not found"); }
	@ExceptionHandler(OrganizationConflictException.class)
	ResponseEntity<ApiError> conflict(OrganizationConflictException exception) {
		return ResponseEntity.status(409).body(new ApiError("ORGANIZATION_CONFLICT",
			"Organization state conflicts with the request", exception.affectedEmployeeIds()));
	}
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiError> dataConflict() { return error(409, "ORGANIZATION_CONFLICT", "Organization state conflicts with the request"); }
	@ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class})
	ResponseEntity<ApiError> invalid() { return error(400, "INVALID_REQUEST", "Organization request is invalid"); }
	private static ResponseEntity<ApiError> error(int status, String code, String message) {
		return ResponseEntity.status(status).body(new ApiError(code, message, java.util.List.of()));
	}
	record ApiError(String code, String message, java.util.List<Long> affectedEmployeeIds) {}
}
