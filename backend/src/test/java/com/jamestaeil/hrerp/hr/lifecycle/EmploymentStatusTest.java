package com.jamestaeil.hrerp.hr.lifecycle;

import static com.jamestaeil.hrerp.hr.lifecycle.LifecycleTypes.EmploymentStatus.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class EmploymentStatusTest {
    @Test
    void exposesTheCompleteTransitionTable() {
        assertEquals(List.of(ON_LEAVE, SUSPENDED, TERMINATED), ACTIVE.allowedNext());
        assertEquals(List.of(ACTIVE, TERMINATED), ON_LEAVE.allowedNext());
        assertEquals(List.of(ACTIVE, TERMINATED), SUSPENDED.allowedNext());
        assertEquals(List.of(), TERMINATED.allowedNext());
    }
}
