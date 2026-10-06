package com.jamestaeil.hrerp.hr.analytics;

import com.jamestaeil.hrerp.hr.employee.domain.EmploymentType;
import com.jamestaeil.hrerp.hr.lifecycle.LifecycleService;
import com.jamestaeil.hrerp.hr.lifecycle.LifecycleTypes.EmploymentStatus;
import com.jamestaeil.hrerp.hr.lifecycle.LifecycleTypes.OrganizationChart;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HrAnalyticsService {
    public enum TenureBand { UNDER_1_YEAR, YEARS_1_TO_3, YEARS_3_TO_5, YEARS_5_TO_10, YEARS_10_PLUS }
    public record Trend(YearMonth month, long hires, long departures) {}
    public record Analytics(LocalDate from, LocalDate to, LocalDate asOf, long activeEmployees,
                            Map<EmploymentType, Long> employmentTypes,
                            Map<TenureBand, Long> tenureBands, List<Trend> trends) {}
    record EmployeeFact(long id, LocalDate hireDate, EmploymentType employmentType, LocalDate terminationDate) {}

    private final JdbcTemplate jdbc;
    private final LifecycleService lifecycle;

    public HrAnalyticsService(JdbcTemplate jdbc, LifecycleService lifecycle) {
        this.jdbc = jdbc;
        this.lifecycle = lifecycle;
    }

    @Transactional(readOnly = true)
    public Analytics analyze(LocalDate from, LocalDate to) {
        validate(from, to);
        OrganizationChart chart = lifecycle.organizationChart(to);
        if (chart.employees().isEmpty()) return aggregate(from, to, Set.of(), List.of());
        Set<Long> scoped = new HashSet<>();
        Set<Long> active = new HashSet<>();
        chart.employees().forEach(employee -> {
            scoped.add(employee.employeeId());
            if (employee.status() == EmploymentStatus.ACTIVE) active.add(employee.employeeId());
        });
        return aggregate(from, to, active, loadFacts(scoped));
    }

    private List<EmployeeFact> loadFacts(Set<Long> employeeIds) {
        String placeholders = String.join(",", java.util.Collections.nCopies(employeeIds.size(), "?"));
        return jdbc.query("""
            SELECT e.id, e.hire_date, e.employment_type, t.termination_date
            FROM employees e LEFT JOIN terminations t ON t.employee_id=e.id
            WHERE e.id IN (%s) ORDER BY e.id
            """.formatted(placeholders), (rs, row) -> new EmployeeFact(rs.getLong("id"),
                rs.getObject("hire_date", LocalDate.class), EmploymentType.valueOf(rs.getString("employment_type")),
                rs.getObject("termination_date", LocalDate.class)), employeeIds.toArray());
    }

    static Analytics aggregate(LocalDate from, LocalDate to, Set<Long> activeIds, List<EmployeeFact> facts) {
        Map<EmploymentType, Long> employment = zeroMap(EmploymentType.class);
        Map<TenureBand, Long> tenure = zeroMap(TenureBand.class);
        Map<YearMonth, long[]> trend = new LinkedHashMap<>();
        for (YearMonth month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1))
            trend.put(month, new long[2]);
        for (EmployeeFact fact : facts) {
            if (activeIds.contains(fact.id())) {
                employment.compute(fact.employmentType(), (key, count) -> count + 1);
                tenure.compute(tenureBand(fact.hireDate(), to), (key, count) -> count + 1);
            }
            if (!fact.hireDate().isBefore(from) && !fact.hireDate().isAfter(to))
                trend.get(YearMonth.from(fact.hireDate()))[0]++;
            if (fact.terminationDate() != null && !fact.terminationDate().isBefore(from)
                    && !fact.terminationDate().isAfter(to))
                trend.get(YearMonth.from(fact.terminationDate()))[1]++;
        }
        List<Trend> trends = new ArrayList<>();
        trend.forEach((month, counts) -> trends.add(new Trend(month, counts[0], counts[1])));
        return new Analytics(from, to, to, activeIds.size(), Map.copyOf(employment), Map.copyOf(tenure), List.copyOf(trends));
    }

    private static TenureBand tenureBand(LocalDate hireDate, LocalDate asOf) {
        if (hireDate.plusYears(1).isAfter(asOf)) return TenureBand.UNDER_1_YEAR;
        if (hireDate.plusYears(3).isAfter(asOf)) return TenureBand.YEARS_1_TO_3;
        if (hireDate.plusYears(5).isAfter(asOf)) return TenureBand.YEARS_3_TO_5;
        if (hireDate.plusYears(10).isAfter(asOf)) return TenureBand.YEARS_5_TO_10;
        return TenureBand.YEARS_10_PLUS;
    }

    private static <E extends Enum<E>> Map<E, Long> zeroMap(Class<E> type) {
        Map<E, Long> result = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) result.put(value, 0L);
        return result;
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)
                || ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) >= 120)
            throw new IllegalArgumentException("Invalid analytics period");
    }
}
