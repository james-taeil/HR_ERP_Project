package com.jamestaeil.hrerp.platform.port;

import java.time.LocalDate;

/** Implement in the caller's transaction. */
public interface AccountAccessScheduler {
    void disableFrom(long employeeId, LocalDate date);
}
