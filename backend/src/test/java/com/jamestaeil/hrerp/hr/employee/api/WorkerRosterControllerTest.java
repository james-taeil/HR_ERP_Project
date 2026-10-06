package com.jamestaeil.hrerp.hr.employee.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jamestaeil.hrerp.hr.employee.application.WorkerRosterService;
import com.jamestaeil.hrerp.hr.employee.application.WorkerRosterIncompleteException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkerRosterControllerTest {
    @Test void downloadsPdfWithoutCaching() throws Exception {
        WorkerRosterService service = mock(WorkerRosterService.class);
        when(service.generate(7)).thenReturn("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkerRosterController(service))
            .setControllerAdvice(new EmployeeApiExceptionHandler()).build();
        mvc.perform(get("/api/hr/employees/7/worker-roster.pdf"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=worker-roster-7.pdf"));
    }

    @Test void reportsMissingLegalFields() throws Exception {
        WorkerRosterService service = mock(WorkerRosterService.class);
        when(service.generate(7)).thenThrow(new WorkerRosterIncompleteException(java.util.List.of("gender", "address")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkerRosterController(service))
            .setControllerAdvice(new EmployeeApiExceptionHandler()).build();
        mvc.perform(get("/api/hr/employees/7/worker-roster.pdf"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("WORKER_ROSTER_INCOMPLETE"))
            .andExpect(jsonPath("$.message").value("Worker roster is missing required fields: gender, address"));
    }
}
