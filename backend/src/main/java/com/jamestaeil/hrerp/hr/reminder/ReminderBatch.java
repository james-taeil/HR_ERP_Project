package com.jamestaeil.hrerp.hr.reminder;

import static com.jamestaeil.hrerp.platform.port.NotificationSender.Audience.*;
import static com.jamestaeil.hrerp.platform.port.NotificationSender.Kind.*;

import com.jamestaeil.hrerp.platform.port.NotificationSender;
import com.jamestaeil.hrerp.platform.port.NotificationSender.Notification;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Component
class ReminderBatch {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final ReminderBatchService service;
    private final Clock clock;

    @Autowired
    ReminderBatch(ReminderBatchService service) {
        this(service, Clock.system(SEOUL));
    }

    ReminderBatch(ReminderBatchService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    void runDaily() {
        service.run(LocalDate.now(clock));
    }
}

@Service
class ReminderBatchService {
    private final JdbcTemplate jdbc;
    private final NotificationSender sender;

    ReminderBatchService(JdbcTemplate jdbc, NotificationSender sender) {
        this.jdbc = jdbc;
        this.sender = sender;
    }

    @Transactional
    public void run(LocalDate today) {
        Integer locked = jdbc.queryForObject("SELECT GET_LOCK('hr_reminder_batch', 10)", Integer.class);
        if (!Integer.valueOf(1).equals(locked)) {
            throw new IllegalStateException("Could not acquire HR reminder batch lock");
        }
        try {
            sendCertifications(today.plusDays(30));
            sendProbationEnds(today.plusDays(14));
            sendStayExpiries(today.plusDays(60));
        } finally {
            jdbc.queryForObject("SELECT RELEASE_LOCK('hr_reminder_batch')", Integer.class);
        }
    }

    private void sendCertifications(LocalDate dueDate) {
        for (Candidate candidate : candidates(
            "SELECT employee_id, id FROM certifications WHERE expires_on = ?", dueDate)) {
            sendOnce("CERTIFICATION_EXPIRY_SELF", candidate, dueDate, EMPLOYEE, CERTIFICATION_EXPIRY);
            sendOnce("CERTIFICATION_EXPIRY_HR", candidate, dueDate, HR, CERTIFICATION_EXPIRY);
        }
    }

    private void sendProbationEnds(LocalDate dueDate) {
        for (Candidate candidate : candidates(
            "SELECT id, id FROM employees WHERE probation_end_date = ?", dueDate)) {
            sendOnce("PROBATION_END_HR", candidate, dueDate, HR, PROBATION_END);
        }
    }

    private void sendStayExpiries(LocalDate dueDate) {
        for (Candidate candidate : candidates(
            "SELECT employee_id, employee_id FROM foreign_worker_profiles WHERE stay_until = ?", dueDate)) {
            sendOnce("STAY_EXPIRY_HR", candidate, dueDate, HR, STAY_EXPIRY);
        }
    }

    private List<Candidate> candidates(String sql, LocalDate dueDate) {
        return jdbc.query(sql, (rs, row) -> new Candidate(rs.getLong(1), rs.getLong(2)), dueDate);
    }

    private void sendOnce(String type, Candidate candidate, LocalDate dueDate,
                          NotificationSender.Audience audience, NotificationSender.Kind kind) {
        int claimed = jdbc.update("""
            INSERT IGNORE INTO reminder_deliveries (reminder_type, employee_id, target_id, due_date)
            VALUES (?, ?, ?, ?)
            """, type, candidate.employeeId(), candidate.targetId(), dueDate);
        if (claimed == 1) {
            sender.send(new Notification(audience, kind, candidate.employeeId(), candidate.targetId(), dueDate));
        }
    }

    private record Candidate(long employeeId, long targetId) {}
}
