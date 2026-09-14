package com.jamestaeil.hrerp.hr.lifecycle;

import java.time.LocalDate;
import java.util.List;

public final class LifecycleTypes {
    private LifecycleTypes() {}

    public enum EmploymentStatus {
        ACTIVE, ON_LEAVE, SUSPENDED, TERMINATED;

        public List<EmploymentStatus> allowedNext() {
            return switch (this) {
                case ACTIVE -> List.of(ON_LEAVE, SUSPENDED, TERMINATED);
                case ON_LEAVE, SUSPENDED -> List.of(ACTIVE, TERMINATED);
                case TERMINATED -> List.of();
            };
        }
    }

    public enum AppointmentType { DEPARTMENT_CHANGE, WORKPLACE_CHANGE, POSITION_CHANGE, STATUS_CHANGE }
    public enum AppointmentStatus { SCHEDULED, APPLIED }

    public record Snapshot(long workplaceId, long departmentId, String position, EmploymentStatus status) {}
    public record Appointment(long id, long employeeId, LocalDate effectiveDate, AppointmentType type,
                              AppointmentStatus appointmentStatus, Snapshot before, Snapshot after,
                              String reason, Long evidenceFileId) {}
    public record Saved(long id, AppointmentStatus status) {}
    public record Department(long id, long workplaceId, Long parentId, String name, int capacity) {}
    public record OrganizationEmployee(long employeeId, long workplaceId, long departmentId,
                                       String position, EmploymentStatus status) {}
    public record OrganizationChart(LocalDate date, List<Department> departments,
                                    List<OrganizationEmployee> employees) {}
    public record Headcount(LocalDate date, List<DepartmentCount> departments) {}
    public record DepartmentCount(long departmentId, int capacity, long current, long difference) {}
}
