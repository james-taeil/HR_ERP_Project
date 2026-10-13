package com.jamestaeil.hrerp.hr.employee.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jamestaeil.hrerp.hr.employee.application.EmployeeSensitiveDataService;
import com.jamestaeil.hrerp.hr.employee.application.EmployeeSensitiveDataService.Masked;
import com.jamestaeil.hrerp.hr.employee.application.EmployeeSensitiveDataService.Plain;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmployeeSensitiveDataControllerTest {
    @Test void returnsMasksForNormalReadAndNoStoreForReveal() throws Exception {
        EmployeeSensitiveDataService service = mock(EmployeeSensitiveDataService.class);
        when(service.masked(11)).thenReturn(new Masked("900101-*******", "******7890"));
        when(service.reveal(11, "급여 확인")).thenReturn(new Plain("9001011234567", "1234567890"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EmployeeSensitiveDataController(service))
            .setControllerAdvice(new EmployeeApiExceptionHandler()).build();
        mvc.perform(get("/api/hr/employees/11/sensitive-data"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.residentRegistrationNumber").value("900101-*******"));
        mvc.perform(post("/api/hr/employees/11/sensitive-data/reveals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"급여 확인\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.residentRegistrationNumber").value("9001011234567"));
    }
}
