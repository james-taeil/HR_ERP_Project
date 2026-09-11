package com.jamestaeil.hrerp.hr.employee.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.jamestaeil.hrerp.hr.employee.application.EmployeeNumberExhaustedException;
import com.jamestaeil.hrerp.platform.port.PlatformIntegrationUnavailableException;
import com.jamestaeil.hrerp.platform.port.EmployeeRegistrationForbiddenException;
import com.jamestaeil.hrerp.platform.port.OrganizationValidationException;

@RestControllerAdvice
public class EmployeeApiExceptionHandler {
	@ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class})
	ResponseEntity<ApiError> badRequest(Exception exception) {
		return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Employee registration request is invalid");
	}

	@ExceptionHandler(EmployeeNumberExhaustedException.class)
	ResponseEntity<ApiError> sequenceExhausted() {
		return response(HttpStatus.CONFLICT, "EMPLOYEE_NUMBER_EXHAUSTED", "Employee number sequence is exhausted");
	}

	@ExceptionHandler(PlatformIntegrationUnavailableException.class)
	ResponseEntity<ApiError> platformUnavailable() {
		return response(HttpStatus.SERVICE_UNAVAILABLE, "PLATFORM_UNAVAILABLE", "Platform validation is unavailable");
	}

	@ExceptionHandler(EmployeeRegistrationForbiddenException.class)
	ResponseEntity<ApiError> forbidden() {
		return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Employee registration is forbidden");
	}

	@ExceptionHandler(OrganizationValidationException.class)
	ResponseEntity<ApiError> invalidOrganization() {
		return response(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ORGANIZATION", "Workplace or department is invalid");
	}

	private static ResponseEntity<ApiError> response(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(new ApiError(code, message));
	}
}
