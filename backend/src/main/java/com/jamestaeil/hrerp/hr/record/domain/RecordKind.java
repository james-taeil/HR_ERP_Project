package com.jamestaeil.hrerp.hr.record.domain;

public enum RecordKind {
    FAMILY, EDUCATION, CAREER, CERTIFICATION;

    public static RecordKind fromPath(String path) {
        return switch (path) {
            case "family-members" -> FAMILY;
            case "educations" -> EDUCATION;
            case "careers" -> CAREER;
            case "certifications" -> CERTIFICATION;
            default -> throw new IllegalArgumentException("Unknown record section");
        };
    }
}
