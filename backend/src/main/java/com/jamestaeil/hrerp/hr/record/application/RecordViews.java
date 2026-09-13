package com.jamestaeil.hrerp.hr.record.application;

import java.time.LocalDate;
import java.util.List;
import com.jamestaeil.hrerp.hr.employee.domain.EmploymentType;
import com.jamestaeil.hrerp.hr.record.domain.RecordData;

public final class RecordViews {
    private RecordViews() {}

    public record Entry(long id, long version, RecordData data) {}
    public record Saved(long id, long version) {}
    public record EmployeeSummary(long id, String employeeNumber, String name, LocalDate birthDate,
                                  String phone, LocalDate hireDate, EmploymentType employmentType,
                                  long workplaceId, long departmentId, String position,
                                  LocalDate probationEndDate, boolean foreignWorker) {}
    public record Card(EmployeeSummary employee, List<Entry> familyMembers, List<Entry> educations,
                       List<Entry> careers, List<Entry> certifications,
                       List<Object> appointments, List<Object> contracts) {}
}
