package com.jamestaeil.hrerp.hr.contract;

import java.time.LocalDate;
import java.util.List;

public final class ContractTypes {
    private ContractTypes() {}
    public enum WageCategory { BASE_PAY, FIXED_ALLOWANCE, VARIABLE_ALLOWANCE, NON_TAXABLE }
    public record Contract(long id, long employeeId, LocalDate contractStart, LocalDate contractEnd,
                           String workLocation, int weeklyWorkMinutes, long agreedMonthlyWage,
                           LocalDate probationStart, LocalDate probationEnd, String probationTerms) {}
    public record ContractCommand(String idempotencyKey, LocalDate contractStart, LocalDate contractEnd,
                                  String workLocation, int weeklyWorkMinutes, long agreedMonthlyWage,
                                  LocalDate probationStart, LocalDate probationEnd, String probationTerms) {}
    public record WageItem(String itemName, WageCategory category, long amount,
                           boolean taxable, boolean ordinaryWage) {}
    public record WageContract(long id, long employeeId, LocalDate effectiveFrom, List<WageItem> items) {}
    public record WageCommand(String idempotencyKey, LocalDate effectiveFrom, List<WageItem> items) {}
    public record Saved(long id) {}
}
