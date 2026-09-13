package com.jamestaeil.hrerp.hr.record.domain;

import java.time.LocalDate;

/** Validated values shared by the record use cases, never JPA entities. */
public sealed interface RecordData {
    record Family(String name, String relationship, LocalDate birthDate, Boolean cohabiting,
                  Boolean dependent, Boolean disabled, Boolean deductionEligible) implements RecordData {
        public Family {
            text(name, 100); text(relationship, 50); required(birthDate);
            required(cohabiting); required(dependent); required(disabled); required(deductionEligible);
        }
    }

    record Education(LocalDate startDate, LocalDate endDate, String institution, String major,
                     Long evidenceFileId) implements RecordData {
        public Education {
            period(startDate, endDate); text(institution, 200);
            if (major != null && major.length() > 200) invalid();
            file(evidenceFileId);
        }
    }

    record Career(LocalDate startDate, LocalDate endDate, String institution, String job,
                  Long evidenceFileId) implements RecordData {
        public Career {
            period(startDate, endDate); text(institution, 200); text(job, 200); file(evidenceFileId);
        }
    }

    record Certification(String name, String issuer, LocalDate acquiredDate, LocalDate expiresOn)
            implements RecordData {
        public Certification { text(name, 200); text(issuer, 200); period(acquiredDate, expiresOn); }
    }

    private static void text(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) invalid();
    }
    private static void required(Object value) { if (value == null) invalid(); }
    private static void file(Long id) { if (id != null && id <= 0) invalid(); }
    private static void period(LocalDate start, LocalDate end) {
        required(start);
        if (end != null && end.isBefore(start)) invalid();
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid personnel record"); }
}
