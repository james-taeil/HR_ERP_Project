package com.jamestaeil.hrerp.hr.analytics;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jamestaeil.hrerp.hr.analytics.HrAnalyticsService.EmployeeFact;
import com.jamestaeil.hrerp.hr.analytics.HrAnalyticsService.TenureBand;
import com.jamestaeil.hrerp.hr.employee.domain.EmploymentType;
import com.jamestaeil.hrerp.hr.lifecycle.LifecycleService;
import com.jamestaeil.hrerp.hr.lifecycle.LifecycleTypes.OrganizationChart;
import com.jamestaeil.hrerp.hr.lifecycle.LifecycleTypes.OrganizationEmployee;
import com.jamestaeil.hrerp.hr.lifecycle.LifecycleTypes.EmploymentStatus;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class HrAnalyticsServiceTest {
    @Test void aggregatesActiveDistributionsAndInclusiveMonthlyTrend() {
        LocalDate from = LocalDate.of(2026, 1, 15), to = LocalDate.of(2026, 3, 10);
        var result = HrAnalyticsService.aggregate(from, to, Set.of(1L, 2L), List.of(
            new EmployeeFact(1, LocalDate.of(2026, 1, 15), EmploymentType.REGULAR, null),
            new EmployeeFact(2, LocalDate.of(2023, 3, 10), EmploymentType.CONTRACT, null),
            new EmployeeFact(3, LocalDate.of(2026, 2, 1), EmploymentType.DAILY, LocalDate.of(2026, 3, 10))));
        assertAll(() -> assertEquals(2, result.activeEmployees()),
            () -> assertEquals(1, result.employmentTypes().get(EmploymentType.REGULAR)),
            () -> assertEquals(0, result.employmentTypes().get(EmploymentType.DAILY)),
            () -> assertEquals(1, result.tenureBands().get(TenureBand.UNDER_1_YEAR)),
            () -> assertEquals(1, result.tenureBands().get(TenureBand.YEARS_3_TO_5)),
            () -> assertEquals(List.of(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3)),
                result.trends().stream().map(HrAnalyticsService.Trend::month).toList()),
            () -> assertEquals(1, result.trends().get(0).hires()),
            () -> assertEquals(1, result.trends().get(1).hires()),
            () -> assertEquals(1, result.trends().get(2).departures()));
    }

    @Test void usesTheEndDateOrganizationScopeAndRejectsLongPeriods() {
        LifecycleService lifecycle = mock(LifecycleService.class);
        when(lifecycle.organizationChart(LocalDate.of(2026, 12, 31)))
            .thenReturn(new OrganizationChart(LocalDate.of(2026, 12, 31), List.of(), List.of()));
        HrAnalyticsService service = new HrAnalyticsService(mock(JdbcTemplate.class), lifecycle);
        assertEquals(0, service.analyze(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).activeEmployees());
        verify(lifecycle).organizationChart(LocalDate.of(2026, 12, 31));
        assertThrows(IllegalArgumentException.class,
            () -> service.analyze(LocalDate.of(2016, 1, 1), LocalDate.of(2026, 1, 1)));
    }

    @SuppressWarnings("unchecked")
    @Test void loadsOnlyEmployeesReturnedByTheAuthorizedOrganizationSnapshot() {
        LocalDate to = LocalDate.of(2026, 12, 31);
        LifecycleService lifecycle = mock(LifecycleService.class);
        when(lifecycle.organizationChart(to)).thenReturn(new OrganizationChart(to, List.of(), List.of(
            new OrganizationEmployee(11, 1, 2, "개발자", EmploymentStatus.ACTIVE),
            new OrganizationEmployee(12, 1, 2, "개발자", EmploymentStatus.ON_LEAVE))));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(new EmployeeFact(11, LocalDate.of(2025, 1, 1), EmploymentType.REGULAR, null),
                new EmployeeFact(12, LocalDate.of(2024, 1, 1), EmploymentType.CONTRACT, null)));
        var result = new HrAnalyticsService(jdbc, lifecycle).analyze(LocalDate.of(2026, 1, 1), to);
        assertEquals(1, result.activeEmployees());
        assertEquals(1, result.employmentTypes().get(EmploymentType.REGULAR));
        assertEquals(0, result.employmentTypes().get(EmploymentType.CONTRACT));
        verify(jdbc).query(contains("WHERE e.id IN (?,?)"), any(org.springframework.jdbc.core.RowMapper.class),
            any(Object[].class));
    }
}
