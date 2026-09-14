package com.jamestaeil.hrerp.platform.port;

import java.time.LocalDate;
import java.util.List;

public interface OrganizationSnapshotReader {
    List<Department> departmentsOn(long actorId, LocalDate date);

    record Department(long id, long workplaceId, Long parentId, String name, int capacity) {}
}
