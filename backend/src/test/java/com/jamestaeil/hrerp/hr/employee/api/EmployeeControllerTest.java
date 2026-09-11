package com.jamestaeil.hrerp.hr.employee.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.jamestaeil.hrerp.hr.employee.application.RegisterEmployeeCommand;
import com.jamestaeil.hrerp.hr.employee.application.RegisterEmployeeResult;
import com.jamestaeil.hrerp.hr.employee.application.RegisterEmployeeService;

class EmployeeControllerTest {
	private RegisterEmployeeService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(RegisterEmployeeService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new EmployeeController(service))
			.setControllerAdvice(new EmployeeApiExceptionHandler())
			.build();
	}

	@Test
	void returnsCreatedEmployeeContract() throws Exception {
		when(service.register(any(RegisterEmployeeCommand.class)))
			.thenReturn(new RegisterEmployeeResult(7, "26000101"));

		mockMvc.perform(post("/api/hr/employees")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"idempotencyKey":"request-1","name":"홍길동","birthDate":"1990-01-01",
				"phone":"010-0000-0000","hireDate":"2026-09-01","employmentType":"REGULAR",
				"workplaceId":1,"departmentId":1,"position":"사원","foreignWorker":false}
				"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", "/api/hr/employees/7"))
			.andExpect(jsonPath("$.employeeId").value(7))
			.andExpect(jsonPath("$.employeeNumber").value("26000101"));
	}

	@Test
	void rejectsUnknownEmploymentTypeWithoutCallingUseCase() throws Exception {
		mockMvc.perform(post("/api/hr/employees")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"idempotencyKey":"request-1","name":"홍길동","birthDate":"1990-01-01",
				"phone":"010-0000-0000","hireDate":"2026-09-01","employmentType":"INTERN",
				"workplaceId":1,"departmentId":1,"position":"사원","foreignWorker":false}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
