package com.jamestaeil.hrerp.platform.port;

public interface FileStorage {
    /** Reject inaccessible, quarantined, missing or wrong-owner evidence. */
    void requireUsableEvidence(long actorId, long employeeId, long fileId);
}
