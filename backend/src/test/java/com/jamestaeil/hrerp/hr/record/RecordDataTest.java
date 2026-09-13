package com.jamestaeil.hrerp.hr.record;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import com.jamestaeil.hrerp.hr.record.domain.RecordData.*;

class RecordDataTest {
    private final LocalDate day = LocalDate.of(2026, 9, 1);

    @Test void preservesEveryFamilyFlagWithoutInferringTaxEligibility() {
        var family = new Family("테스트 가족", "자녀", day, true, true, false, false);
        assertTrue(family.cohabiting()); assertTrue(family.dependent());
        assertFalse(family.disabled()); assertFalse(family.deductionEligible());
        assertThrows(IllegalArgumentException.class,
            () -> new Family("테스트 가족", "자녀", day, true, true, false, null));
    }

    @Test void acceptsOpenEndedAndSameDayPeriods() {
        assertDoesNotThrow(() -> new Education(day, null, "테스트 학교", null, null));
        assertDoesNotThrow(() -> new Career(day, day, "테스트 기관", "개발", 1L));
        assertDoesNotThrow(() -> new Certification("테스트 자격", "테스트 기관", day, null));
        assertDoesNotThrow(() -> new Certification("테스트 자격", "테스트 기관", day, day));
    }

    @Test void rejectsReversedPeriodsAndInvalidReferences() {
        assertThrows(IllegalArgumentException.class, () -> new Education(day, day.minusDays(1), "학교", null, null));
        assertThrows(IllegalArgumentException.class, () -> new Career(day, day.minusDays(1), "기관", "직무", null));
        assertThrows(IllegalArgumentException.class, () -> new Certification("자격", "기관", day, day.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> new Education(day, null, "학교", null, 0L));
        assertThrows(IllegalArgumentException.class, () -> new Career(day, null, "기관", " ", null));
        assertThrows(IllegalArgumentException.class, () -> new Certification("x".repeat(201), "기관", day, null));
        assertThrows(IllegalArgumentException.class, () -> new Education(null, null, "학교", null, null));
    }
}
