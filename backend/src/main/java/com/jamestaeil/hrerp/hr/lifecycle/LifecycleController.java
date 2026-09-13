package com.jamestaeil.hrerp.hr.lifecycle;

import static com.jamestaeil.hrerp.hr.lifecycle.LifecycleTypes.*;

import com.jamestaeil.hrerp.hr.employee.api.ApiError;
import com.jamestaeil.hrerp.hr.lifecycle.LifecycleService.AppointmentCommand;
import com.jamestaeil.hrerp.hr.lifecycle.LifecycleService.TerminationCommand;
import com.jamestaeil.hrerp.platform.port.PlatformIntegrationUnavailableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hr")
public class LifecycleController {
    private final LifecycleService service;
    public LifecycleController(LifecycleService service) { this.service = service; }

    public record AppointmentRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String idempotencyKey,
        @NotNull LocalDate effectiveDate, @NotNull AppointmentType type,
        Long workplaceId, Long departmentId, @Size(max = 100) String position,
        EmploymentStatus status, @NotBlank @Size(max = 500) String reason, Long evidenceFileId) {}

    public record TerminationRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String idempotencyKey,
        @NotNull LocalDate terminationDate,
        @NotNull @Pattern(regexp = "[A-Z0-9_-]{1,50}") String reasonCode,
        boolean separationCertificateRequired) {}

    @PostMapping("/employees/{employeeId}/appointments")
    ResponseEntity<Saved> appoint(@PathVariable long employeeId, @Valid @RequestBody AppointmentRequest request) {
        Saved saved = service.appoint(employeeId, new AppointmentCommand(request.idempotencyKey(),
            request.effectiveDate(), request.type(), request.workplaceId(), request.departmentId(), request.position(),
            request.status(), request.reason(), request.evidenceFileId()));
        return ResponseEntity.created(URI.create("/api/hr/employees/" + employeeId + "/appointments/" + saved.id()))
            .body(saved);
    }

    @GetMapping("/employees/{employeeId}/appointments")
    List<Appointment> appointments(@PathVariable long employeeId) { return service.appointments(employeeId); }

    @PostMapping("/employees/{employeeId}/termination")
    ResponseEntity<TerminationSaved> terminate(@PathVariable long employeeId,
                                                @Valid @RequestBody TerminationRequest request) {
        long id = service.terminate(employeeId, new TerminationCommand(request.idempotencyKey(),
            request.terminationDate(), request.reasonCode(), request.separationCertificateRequired()));
        return ResponseEntity.created(URI.create("/api/hr/employees/" + employeeId + "/termination"))
            .body(new TerminationSaved(id));
    }

    @GetMapping("/organization-chart")
    OrganizationChart organizationChart(@RequestParam LocalDate date) { return service.organizationChart(date); }

    @GetMapping("/headcount")
    Headcount headcount(@RequestParam LocalDate date) { return service.headcount(date); }

    record TerminationSaved(long id) {}
}

@RestControllerAdvice(assignableTypes = LifecycleController.class)
class LifecycleExceptionHandler {
    @ExceptionHandler(InvalidStatusTransitionException.class)
    ResponseEntity<TransitionError> transition(InvalidStatusTransitionException exception) {
        return ResponseEntity.status(409).body(new TransitionError("INVALID_STATUS_TRANSITION",
            "Requested employment status transition is not allowed", exception.allowed()));
    }
    @ExceptionHandler(LifecycleConflictException.class)
    ResponseEntity<ApiError> conflict() { return error(409, "LIFECYCLE_CONFLICT", "Reload before changing lifecycle data"); }
    @ExceptionHandler(LifecycleNotFoundException.class)
    ResponseEntity<ApiError> notFound() { return error(404, "EMPLOYEE_NOT_FOUND", "Employee not found"); }
    @ExceptionHandler(LifecycleForbiddenException.class)
    ResponseEntity<ApiError> forbidden() { return error(403, "FORBIDDEN", "Lifecycle access is forbidden"); }
    @ExceptionHandler(PlatformIntegrationUnavailableException.class)
    ResponseEntity<ApiError> unavailable() { return error(503, "PLATFORM_UNAVAILABLE", "Platform validation is unavailable"); }
    @ExceptionHandler({IllegalArgumentException.class,
        org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalid() { return error(400, "INVALID_REQUEST", "Lifecycle request is invalid"); }
    private static ResponseEntity<ApiError> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
    record TransitionError(String code, String message, List<EmploymentStatus> allowedNext) {}
}
