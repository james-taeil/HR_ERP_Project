package com.jamestaeil.hrerp.platform.port;

import java.util.Set;

public interface EmployeeSearchScopeReader {
    Scope requireReadableScope(long actorId);
    record Scope(Long selfEmployeeId, Set<Long> departmentIds) {
        public Scope { departmentIds = Set.copyOf(departmentIds); }
    }
}
