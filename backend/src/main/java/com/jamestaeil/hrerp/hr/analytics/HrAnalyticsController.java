package com.jamestaeil.hrerp.hr.analytics;

import com.jamestaeil.hrerp.hr.employee.api.ApiError;
import com.jamestaeil.hrerp.platform.port.PlatformIntegrationUnavailableException;
import com.jamestaeil.hrerp.platform.port.RecordAccessForbiddenException;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/hr/analytics")
public class HrAnalyticsController {
    private final HrAnalyticsService service;
    public HrAnalyticsController(HrAnalyticsService service) { this.service = service; }

    @GetMapping
    HrAnalyticsService.Analytics analytics(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.analyze(from, to);
    }
}

@RestControllerAdvice(assignableTypes = HrAnalyticsController.class)
class HrAnalyticsExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> invalid() { return error(400, "INVALID_ANALYTICS_PERIOD", "Analytics period is invalid"); }
    @ExceptionHandler(RecordAccessForbiddenException.class)
    ResponseEntity<ApiError> forbidden() { return error(403, "FORBIDDEN", "Analytics access is forbidden"); }
    @ExceptionHandler(PlatformIntegrationUnavailableException.class)
    ResponseEntity<ApiError> unavailable() { return error(503, "PLATFORM_UNAVAILABLE", "Platform validation is unavailable"); }
    private static ResponseEntity<ApiError> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
}
