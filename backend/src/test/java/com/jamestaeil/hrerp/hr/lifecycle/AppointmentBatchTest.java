package com.jamestaeil.hrerp.hr.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class AppointmentBatchTest {
    @Test
    void runsAtMidnightInSeoul() throws Exception {
        Method method = AppointmentBatch.class.getDeclaredMethod("applyDue");
        Scheduled schedule = method.getAnnotation(Scheduled.class);
        assertEquals("0 0 0 * * *", schedule.cron());
        assertEquals("Asia/Seoul", schedule.zone());
    }
}
