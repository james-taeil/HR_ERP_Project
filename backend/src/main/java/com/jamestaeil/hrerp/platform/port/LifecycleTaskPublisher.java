package com.jamestaeil.hrerp.platform.port;

import java.time.LocalDate;

/** Implement as transactional task creation, not a best-effort message send. */
public interface LifecycleTaskPublisher {
    void publish(Task task);

    record Task(Kind kind, long employeeId, LocalDate terminationDate) {}
    enum Kind { LEAVE_SETTLEMENT, INSURANCE_LOSS, RETIREMENT_INCOME, ASSET_RETURN }
}
