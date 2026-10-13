package com.jamestaeil.hrerp.hr.employee.api;

import com.jamestaeil.hrerp.hr.employee.application.EmployeeSensitiveDataService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hr/employees/{employeeId}/sensitive-data")
public class EmployeeSensitiveDataController {
    private final EmployeeSensitiveDataService service;
    public EmployeeSensitiveDataController(EmployeeSensitiveDataService service) { this.service = service; }
    public record SaveRequest(@NotBlank @Pattern(regexp = "[0-9-]{13,14}") String residentRegistrationNumber,
                              @NotBlank @Pattern(regexp = "[0-9-]{6,40}") String bankAccountNumber) {}
    public record RevealRequest(@NotBlank @Size(max = 500) String reason) {}

    @PutMapping
    EmployeeSensitiveDataService.Masked save(@PathVariable long employeeId, @Valid @RequestBody SaveRequest request) {
        return service.save(employeeId, request.residentRegistrationNumber(), request.bankAccountNumber());
    }
    @GetMapping
    EmployeeSensitiveDataService.Masked masked(@PathVariable long employeeId) { return service.masked(employeeId); }
    @PostMapping("/reveals")
    ResponseEntity<EmployeeSensitiveDataService.Plain> reveal(@PathVariable long employeeId,
            @Valid @RequestBody RevealRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.reveal(employeeId, request.reason()));
    }
}
