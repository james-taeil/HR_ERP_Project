package com.jamestaeil.hrerp.hr.employee.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jamestaeil.hrerp.hr.employee.application.RegisterEmployeeResult;
import com.jamestaeil.hrerp.hr.employee.application.RegisterEmployeeService;
import com.jamestaeil.hrerp.hr.employee.application.EmployeeSearchService;
import com.jamestaeil.hrerp.hr.employee.application.EmployeeSearchService.Criteria;
import com.jamestaeil.hrerp.hr.employee.application.EmployeeSearchService.Page;
import com.jamestaeil.hrerp.hr.employee.domain.EmploymentType;
import com.jamestaeil.hrerp.hr.lifecycle.LifecycleTypes.EmploymentStatus;
import java.time.LocalDate;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/hr/employees")
public class EmployeeController {
	private final RegisterEmployeeService service;
	private final EmployeeSearchService search;

	public EmployeeController(RegisterEmployeeService service, EmployeeSearchService search) {
		this.service = service;
		this.search = search;
	}

	@GetMapping
	public Page search(@RequestParam(required = false) Long workplaceId,
			@RequestParam(required = false) Long departmentId,
			@RequestParam(required = false) EmploymentStatus employmentStatus,
			@RequestParam(required = false) EmploymentType employmentType,
			@RequestParam(required = false) LocalDate hireDateFrom,
			@RequestParam(required = false) LocalDate hireDateTo,
			@RequestParam(required = false) String position,
			@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "0") long afterId,
			@RequestParam(defaultValue = "50") int limit) {
		return search.search(new Criteria(workplaceId, departmentId, employmentStatus, employmentType,
			hireDateFrom, hireDateTo, position, query, afterId, limit));
	}

	@PostMapping
	public ResponseEntity<RegisterEmployeeResponse> register(@Valid @RequestBody RegisterEmployeeRequest request) {
		RegisterEmployeeResult result = service.register(request.toCommand());
		return ResponseEntity.created(URI.create("/api/hr/employees/" + result.employeeId()))
			.body(new RegisterEmployeeResponse(result.employeeId(), result.employeeNumber()));
	}
}
