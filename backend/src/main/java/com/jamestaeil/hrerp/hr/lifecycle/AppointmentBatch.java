package com.jamestaeil.hrerp.hr.lifecycle;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AppointmentBatch {
    private final LifecycleService service;
    AppointmentBatch(LifecycleService service) { this.service = service; }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    void applyDue() { service.applyDue(LocalDate.now(ZoneId.of("Asia/Seoul"))); }
}
