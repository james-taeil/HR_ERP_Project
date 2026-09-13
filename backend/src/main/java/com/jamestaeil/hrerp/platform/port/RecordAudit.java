package com.jamestaeil.hrerp.platform.port;

public interface RecordAudit {
    /** Implement as append-only protected storage in the caller's transaction, not application logs. */
    void changed(long actorId, long employeeId, String section, long recordId, Object before, Object after);
    void viewed(long actorId, long employeeId, String section);
}
