package com.jamestaeil.hrerp.hr.contract;

import static com.jamestaeil.hrerp.hr.contract.ContractTypes.*;

import com.jamestaeil.hrerp.hr.employee.api.ApiError;
import com.jamestaeil.hrerp.platform.port.PlatformIntegrationUnavailableException;
import com.jamestaeil.hrerp.platform.port.RecordAccessForbiddenException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hr/employees/{employeeId}")
public class ContractController {
    private final ContractService service;
    public ContractController(ContractService service) { this.service = service; }

    public record ContractRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String idempotencyKey,
        @NotNull LocalDate contractStart, @NotNull LocalDate contractEnd,
        @NotBlank @Size(max = 200) String workLocation,
        @Min(1) @Max(10080) int weeklyWorkMinutes, @PositiveOrZero long agreedMonthlyWage,
        LocalDate probationStart, LocalDate probationEnd, @Size(max = 500) String probationTerms) {}
    public record WageItemRequest(@NotBlank @Size(max = 100) String itemName,
        @NotNull WageCategory category, @PositiveOrZero long amount,
        @NotNull Boolean taxable, @NotNull Boolean ordinaryWage) {}
    public record WageRequest(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String idempotencyKey,
        @NotNull LocalDate effectiveFrom, @NotEmpty @Size(max = 100) List<@Valid WageItemRequest> items) {}

    @PostMapping("/contracts")
    ResponseEntity<Saved> createContract(@PathVariable long employeeId, @Valid @RequestBody ContractRequest r) {
        Saved saved = service.createContract(employeeId, new ContractCommand(r.idempotencyKey(), r.contractStart(),
            r.contractEnd(), r.workLocation(), r.weeklyWorkMinutes(), r.agreedMonthlyWage(), r.probationStart(),
            r.probationEnd(), r.probationTerms()));
        return ResponseEntity.created(URI.create("/api/hr/employees/" + employeeId + "/contracts/" + saved.id())).body(saved);
    }
    @GetMapping("/contracts") List<Contract> contracts(@PathVariable long employeeId) { return service.contracts(employeeId); }

    @PostMapping("/wage-contracts")
    ResponseEntity<Saved> createWage(@PathVariable long employeeId, @Valid @RequestBody WageRequest r) {
        List<WageItem> items = r.items().stream().map(i -> new WageItem(i.itemName(), i.category(), i.amount(),
            i.taxable(), i.ordinaryWage())).toList();
        Saved saved = service.createWage(employeeId, new WageCommand(r.idempotencyKey(), r.effectiveFrom(), items));
        return ResponseEntity.created(URI.create("/api/hr/employees/" + employeeId + "/wage-contracts/" + saved.id())).body(saved);
    }
    @GetMapping("/wage-contracts") List<WageContract> wages(@PathVariable long employeeId) { return service.wages(employeeId); }
}

@RestControllerAdvice(assignableTypes = ContractController.class)
class ContractExceptionHandler {
    @ExceptionHandler(ContractConflictException.class)
    ResponseEntity<ApiError> conflict() { return error(409, "CONTRACT_CONFLICT", "Contract request conflicts with existing history"); }
    @ExceptionHandler(ContractNotFoundException.class)
    ResponseEntity<ApiError> notFound() { return error(404, "EMPLOYEE_NOT_FOUND", "Employee not found"); }
    @ExceptionHandler(RecordAccessForbiddenException.class)
    ResponseEntity<ApiError> forbidden() { return error(403, "FORBIDDEN", "Contract access is forbidden"); }
    @ExceptionHandler(PlatformIntegrationUnavailableException.class)
    ResponseEntity<ApiError> unavailable() { return error(503, "PLATFORM_UNAVAILABLE", "Platform validation is unavailable"); }
    @ExceptionHandler({IllegalArgumentException.class,
        org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> invalid() { return error(400, "INVALID_REQUEST", "Contract request is invalid"); }
    private static ResponseEntity<ApiError> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
}
