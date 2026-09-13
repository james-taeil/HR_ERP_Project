package com.jamestaeil.hrerp.hr.record.api;

import jakarta.persistence.OptimisticLockException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.jamestaeil.hrerp.hr.employee.api.ApiError;
import com.jamestaeil.hrerp.hr.record.application.*;
import com.jamestaeil.hrerp.platform.port.*;

@Order(-1)
@RestControllerAdvice(assignableTypes = RecordController.class)
public class RecordExceptionHandler {
    @ExceptionHandler(RecordNotFoundException.class)
    ResponseEntity<ApiError> notFound() { return error(404, "RECORD_NOT_FOUND", "Record not found"); }
    @ExceptionHandler({RecordConflictException.class, OptimisticLockException.class, OptimisticLockingFailureException.class})
    ResponseEntity<ApiError> conflict() { return error(409, "RECORD_CONFLICT", "Reload the record before editing"); }
    @ExceptionHandler(RecordAccessForbiddenException.class)
    ResponseEntity<ApiError> forbidden() { return error(403, "FORBIDDEN", "Record access is forbidden"); }
    @ExceptionHandler(PlatformIntegrationUnavailableException.class)
    ResponseEntity<ApiError> unavailable() { return error(503, "PLATFORM_UNAVAILABLE", "Platform validation is unavailable"); }
    @ExceptionHandler({IllegalArgumentException.class,
        org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalid() { return error(400, "INVALID_REQUEST", "Personnel record request is invalid"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> failed() { return error(500, "RECORD_FAILED", "Personnel record operation failed"); }
    private static ResponseEntity<ApiError> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
}
