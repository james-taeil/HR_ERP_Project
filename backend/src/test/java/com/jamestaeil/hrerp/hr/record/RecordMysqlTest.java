package com.jamestaeil.hrerp.hr.record;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.jamestaeil.hrerp.hr.record.api.*;
import com.jamestaeil.hrerp.hr.record.application.*;
import com.jamestaeil.hrerp.hr.record.application.RecordViews.*;
import com.jamestaeil.hrerp.hr.record.domain.*;
import com.jamestaeil.hrerp.platform.port.*;

/** Run only against a disposable, explicitly selected hr_record_test schema. */
@SpringBootTest(properties = {"spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@EnabledIfEnvironmentVariable(named = "HR_RECORD_MYSQL_TEST_URL", matches = ".+/hr_record_test(?:\\?.*)?$")
class RecordMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("HR_RECORD_MYSQL_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_USER", "root"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_PASSWORD", ""));
    }
    @Autowired RecordService service;
    @Autowired DataSource source;
    @Autowired EntityManagerFactory emf;
    @MockitoBean CurrentActorProvider actors;
    @MockitoBean AuthorizationChecker authorization;
    @MockitoBean FileStorage files;
    @MockitoBean RecordAudit audit;
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private long employee;
    private long otherEmployee;
    private static final AtomicInteger numbers = new AtomicInteger(98000000);
    private final LocalDate date = LocalDate.of(2026, 1, 1);
    private final RecordData.Family family = new RecordData.Family("테스트가족", "자녀", date, true, true, false, false);

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(source);
        employee = createEmployee();
        otherEmployee = createEmployee();
        when(actors.requireActorId()).thenReturn(9L);
        mvc = MockMvcBuilders.standaloneSetup(new RecordController(service))
            .setControllerAdvice(new RecordExceptionHandler()).build();
    }
    private long createEmployee() {
        String number = Integer.toString(numbers.incrementAndGet());
        jdbc.update("""
            INSERT INTO employees (employee_number, employee_name, birth_date, phone, hire_date,
                employment_type, workplace_id, department_id, position_name)
            VALUES (?, '테스트사원', '1990-01-01', 'TEST', '2026-01-01', 'REGULAR', 1, 1, '테스트')
            """, number);
        return jdbc.queryForObject("SELECT id FROM employees WHERE employee_number = ?", Long.class, number);
    }
    @AfterEach void cleanup() {
        // Only synthetic rows created by this test; never clear an entire schema.
        for (String table : List.of("record_mutation_requests", "family_members", "educations", "careers", "certifications")) {
            jdbc.update("DELETE FROM " + table + " WHERE employee_id IN (?, ?)", employee, otherEmployee);
        }
        jdbc.update("DELETE FROM employees WHERE id IN (?, ?)", employee, otherEmployee);
    }

    @Test void crudRoundTripAndFiveQueryCard() throws Exception {
        Saved saved = service.save(employee, null, "family", null, family);
        var changed = new RecordData.Family("테스트가족", "자녀", date, false, true, true, true);
        Saved updated = service.save(employee, saved.id(), "edit", saved.version(), changed);
        assertTrue(updated.version() > saved.version());
        assertEquals(changed, service.list(employee, RecordKind.FAMILY).getFirst().data());
        assertThrows(RecordConflictException.class, () -> service.save(employee, saved.id(), "stale", saved.version(), family));
        service.save(employee, null, "education", null, new RecordData.Education(date, null, "테스트학교", "전공", null));
        service.save(employee, null, "career", null, new RecordData.Career(date, date, "테스트기관", "직무", 11L));
        service.save(employee, null, "cert", null, new RecordData.Certification("테스트자격", "기관", date, null));
        verify(files).requireUsableEvidence(9, employee, 11);
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var card = service.card(employee);
        assertEquals(5, stats.getPrepareStatementCount());
        assertEquals(1, card.familyMembers().size());
        assertEquals(1, card.educations().size());
        assertEquals(1, card.careers().size());
        assertEquals(1, card.certifications().size());
        assertTrue(card.appointments().isEmpty()); assertTrue(card.contracts().isEmpty());
        mvc.perform(get("/api/hr/employees/{id}/record", employee))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7))
            .andExpect(jsonPath("$.employee.employeeNumber").isString())
            .andExpect(jsonPath("$.employee.encryptedRegistrationNumber").doesNotExist())
            .andExpect(jsonPath("$.familyMembers[0].data.deductionEligible").value(true));
    }

    @Test void invalidJsonPeriodsAndMissingFlagsNeverPersist() throws Exception {
        for (String path : List.of("educations", "careers")) {
            mvc.perform(post("/api/hr/employees/{id}/" + path, employee).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"idempotencyKey":"bad-period","data":{"institution":"테스트기관","job":"직무",
                    "startDate":"2026-01-02","endDate":"2026-01-01"}}
                    """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(post("/api/hr/employees/{id}/family-members", employee).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"idempotencyKey":"bad-family","data":{"name":"테스트가족","relationship":"자녀",
                "birthDate":"2020-01-01","cohabiting":true,"dependent":true,"disabled":false}}
                """))
            .andExpect(status().isBadRequest());
        assertEquals(0, count("record_mutation_requests"));
        assertEquals(0, count("educations")); assertEquals(0, count("careers")); assertEquals(0, count("family_members"));
    }

    @Test void ownershipAndAuthorizationProtectAllSections() throws Exception {
        Saved saved = service.save(otherEmployee, null, "other-family", null, family);
        assertThrows(RecordNotFoundException.class, () -> service.save(employee, saved.id(), "wrong-owner", saved.version(), family));
        doThrow(new RecordAccessForbiddenException()).when(authorization).checkCanReadRecord(9, employee);
        doThrow(new RecordAccessForbiddenException()).when(authorization).checkCanWriteRecord(9, employee);
        mvc.perform(get("/api/hr/employees/{id}/record", employee)).andExpect(status().isForbidden());
        for (String path : List.of("family-members", "educations", "careers", "certifications")) {
            mvc.perform(get("/api/hr/employees/{id}/" + path, employee)).andExpect(status().isForbidden());
        }
        assertThrows(RecordAccessForbiddenException.class, () -> service.save(employee, null, "denied", null, family));
        assertEquals(0, count("family_members")); assertEquals(0, count("record_mutation_requests"));
    }

    @Test void auditAndFileFailuresRollbackMutationAndClaim() {
        doThrow(new PlatformIntegrationUnavailableException()).when(audit)
            .changed(eq(9L), eq(employee), eq("FAMILY"), anyLong(), isNull(), any());
        assertThrows(PlatformIntegrationUnavailableException.class, () -> service.save(employee, null, "audit-fail", null, family));
        assertEquals(0, count("family_members")); assertEquals(0, count("record_mutation_requests"));
        doThrow(new RecordAccessForbiddenException()).when(files).requireUsableEvidence(9, employee, 12);
        assertThrows(RecordAccessForbiddenException.class, () -> service.save(employee, null, "file-fail", null,
            new RecordData.Education(date, null, "학교", null, 12L)));
        assertEquals(0, count("educations")); assertEquals(0, count("record_mutation_requests"));
    }

    @Test void concurrentReplayCreatesOneRowAndOriginalResult() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Saved> task = () -> { start.await(); return service.save(employee, null, "same-key", null, family); };
            var first = executor.submit(task); var second = executor.submit(task); start.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, count("family_members")); assertEquals(1, count("record_mutation_requests"));
        assertThrows(RecordConflictException.class, () -> service.save(otherEmployee, null, "same-key", null, family));
        when(actors.requireActorId()).thenReturn(10L);
        service.save(employee, null, "same-key", null, family);
        assertEquals(2, count("family_members"));
    }

    @Test void postAndPutContractsAndNoDeletion() throws Exception {
        String body = """
            {"idempotencyKey":"api-cert","data":{"name":"테스트자격","issuer":"기관","acquiredDate":"2026-01-01"}}
            """;
        mvc.perform(post("/api/hr/employees/{id}/certifications", employee)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.version").value(0));
        Entry entry = service.list(employee, RecordKind.CERTIFICATION).getFirst();
        mvc.perform(put("/api/hr/employees/{id}/certifications/{child}", employee, entry.id())
            .contentType(MediaType.APPLICATION_JSON).content(body.replace("api-cert", "api-update")))
            .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/hr/employees/{id}/certifications/{child}", employee, entry.id()))
            .andExpect(status().isMethodNotAllowed());
        assertEquals(1, count("certifications"));
    }

    @Test void everySectionUpdatesWithoutChangingEmployeeNumber() {
        List<RecordData> originals = List.of(family, new RecordData.Education(date, null, "학교", null, null),
            new RecordData.Career(date, null, "기관", "직무", null), new RecordData.Certification("자격", "기관", date, null));
        List<RecordData> replacements = List.of(
            new RecordData.Family("수정가족", "자녀", date, false, false, false, false),
            new RecordData.Education(date, date, "수정학교", "전공", null),
            new RecordData.Career(date, date, "수정기관", "수정직무", null),
            new RecordData.Certification("수정자격", "수정기관", date, date));
        String number = service.card(employee).employee().employeeNumber();
        for (int i = 0; i < originals.size(); i++) {
            var saved = service.save(employee, null, "create-" + i, null, originals.get(i));
            var updated = service.save(employee, saved.id(), "update-" + i, saved.version(), replacements.get(i));
            assertEquals(1, updated.version());
            assertEquals(replacements.get(i), service.list(employee, RecordKind.values()[i]).getFirst().data());
        }
        assertEquals(number, service.card(employee).employee().employeeNumber());
    }

    @Test void concurrentUpdatesRejectOneStaleVersion() throws Exception {
        var saved = service.save(employee, null, "original", null, family);
        var changed = new RecordData.Family("변경가족", "자녀", date, false, true, false, false);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Void> task = () -> {
                start.await();
                try {
                    service.save(employee, saved.id(), "update-" + Thread.currentThread().threadId(), saved.version(), changed);
                    succeeded.incrementAndGet();
                } catch (RecordConflictException | org.springframework.dao.OptimisticLockingFailureException e) {
                    conflicted.incrementAndGet();
                }
                return null;
            };
            var first = executor.submit(task); var second = executor.submit(task); start.countDown();
            first.get(15, TimeUnit.SECONDS); second.get(15, TimeUnit.SECONDS);
        }
        assertEquals(1, succeeded.get()); assertEquals(1, conflicted.get());
        assertEquals(1, service.list(employee, RecordKind.FAMILY).getFirst().version());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE employee_id = ?", Integer.class, employee);
    }
}
