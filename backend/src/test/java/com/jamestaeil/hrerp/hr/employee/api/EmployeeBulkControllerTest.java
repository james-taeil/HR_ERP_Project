package com.jamestaeil.hrerp.hr.employee.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jamestaeil.hrerp.hr.employee.application.EmployeeBulkService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmployeeBulkControllerTest {
    private EmployeeBulkService service;
    private MockMvc mvc;
    @BeforeEach void setup() {
        service = mock(EmployeeBulkService.class);
        mvc = MockMvcBuilders.standaloneSetup(new EmployeeBulkController(service))
            .setControllerAdvice(new EmployeeApiExceptionHandler()).build();
    }
    @Test void downloadsVersionedXlsxTemplate() throws Exception {
        mvc.perform(get("/api/hr/employees/bulk/template"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=employee-bulk-template.xlsx"))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
    @Test void returnsRowValidationSummary() throws Exception {
        when(service.validate(any())).thenReturn(new EmployeeBulkService.Validation("token", 2, 1, 1,
            List.of(new EmployeeBulkService.RowError(3, "INVALID_ROW", "Row data is invalid")), Instant.now()));
        MockMultipartFile file = new MockMultipartFile("file", "employees.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1});
        mvc.perform(multipart("/api/hr/employees/bulk/validations").file(file))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalRows").value(2))
            .andExpect(jsonPath("$.errors[0].rowNumber").value(3));
    }
}
