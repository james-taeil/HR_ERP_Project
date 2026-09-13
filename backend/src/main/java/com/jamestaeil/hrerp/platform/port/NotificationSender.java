package com.jamestaeil.hrerp.platform.port;

import java.time.LocalDate;

/** Platform notification port. Implementations must participate in the caller's transaction. */
public interface NotificationSender {
    void send(Notification notification);

    record Notification(
        Audience audience,
        Kind kind,
        long employeeId,
        long targetId,
        LocalDate dueDate
    ) {}

    enum Audience { EMPLOYEE, HR }

    enum Kind { CERTIFICATION_EXPIRY, PROBATION_END, STAY_EXPIRY }
}
