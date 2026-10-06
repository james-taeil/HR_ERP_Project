package com.jamestaeil.hrerp.hr.analytics;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jamestaeil.hrerp.hr.analytics.HrAnalyticsService.Analytics;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HrAnalyticsControllerTest {
    @Test void returnsAllFourMetricsAndTheAsOfDate() throws Exception {
        HrAnalyticsService service = mock(HrAnalyticsService.class);
        LocalDate from = LocalDate.of(2026, 1, 1), to = LocalDate.of(2026, 3, 31);
        when(service.analyze(from, to)).thenReturn(new Analytics(from, to, to, 7, Map.of(), Map.of(), List.of()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HrAnalyticsController(service))
            .setControllerAdvice(new HrAnalyticsExceptionHandler()).build();
        mvc.perform(get("/api/hr/analytics").param("from", "2026-01-01").param("to", "2026-03-31"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.asOf").value("2026-03-31"))
            .andExpect(jsonPath("$.activeEmployees").value(7))
            .andExpect(jsonPath("$.employmentTypes").isMap())
            .andExpect(jsonPath("$.tenureBands").isMap()).andExpect(jsonPath("$.trends").isArray());
    }

    @Test void rejectsMissingDates() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HrAnalyticsController(mock(HrAnalyticsService.class)))
            .setControllerAdvice(new HrAnalyticsExceptionHandler()).build();
        mvc.perform(get("/api/hr/analytics")).andExpect(status().isBadRequest());
    }
}
