package com.jamestaeil.hrerp.hr.employee.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jamestaeil.hrerp.hr.employee.application.RegisterEmployeeResult;
import com.jamestaeil.hrerp.hr.employee.application.RegisterEmployeeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/hr/employees")
public class EmployeeController {
	private final RegisterEmployeeService service;

	public EmployeeController(RegisterEmployeeService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<RegisterEmployeeResponse> register(@Valid @RequestBody RegisterEmployeeRequest request) {
		RegisterEmployeeResult result = service.register(request.toCommand());
		return ResponseEntity.created(URI.create("/api/hr/employees/" + result.employeeId()))
			.body(new RegisterEmployeeResponse(result.employeeId(), result.employeeNumber()));
	}
}
