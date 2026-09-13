package com.jamestaeil.hrerp.hr.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class ReminderBatchTest {
    @Test
    void runsAtNineInSeoulUsingSeoulCalendarDate() throws Exception {
        Method method = ReminderBatch.class.getDeclaredMethod("runDaily");
        Scheduled schedule = method.getAnnotation(Scheduled.class);
        assertEquals("0 0 9 * * *", schedule.cron());
        assertEquals("Asia/Seoul", schedule.zone());

        ReminderBatchService service = mock(ReminderBatchService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T15:30:00Z"), ZoneId.of("Asia/Seoul"));
        new ReminderBatch(service, clock).runDaily();
        verify(service).run(LocalDate.of(2026, 1, 2));
    }
}
