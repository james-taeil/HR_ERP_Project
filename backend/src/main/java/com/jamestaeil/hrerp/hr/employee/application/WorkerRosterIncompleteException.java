package com.jamestaeil.hrerp.hr.employee.application;

import java.util.List;

public class WorkerRosterIncompleteException extends RuntimeException {
    private final List<String> missingFields;

    public WorkerRosterIncompleteException(List<String> missingFields) {
        this.missingFields = List.copyOf(missingFields);
    }

    public List<String> missingFields() { return missingFields; }
}
