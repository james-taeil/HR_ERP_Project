package com.jamestaeil.hrerp.hr.employee.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.jamestaeil.hrerp.platform.port.AuthorizationChecker;
import com.jamestaeil.hrerp.platform.port.CurrentActorProvider;
import com.jamestaeil.hrerp.platform.port.RecordAudit;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HR_RECORD_MYSQL_TEST_URL", matches = ".+/hr_record_test(?:\\?.*)?$")
class WorkerRosterMysqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("HR_RECORD_MYSQL_TEST_URL"));
        p.add("spring.datasource.username", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_USER", "root"));
        p.add("spring.datasource.password", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_PASSWORD", ""));
    }
    @Autowired WorkerRosterService service;
    @Autowired DataSource source;
    @MockitoBean CurrentActorProvider actors;
    @MockitoBean AuthorizationChecker authorization;
    @MockitoBean RecordAudit audit;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(source); when(actors.requireActorId()).thenReturn(55L);
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM employment_contracts WHERE employee_id IN (SELECT id FROM employees WHERE employee_number LIKE '39%')");
        jdbc.update("DELETE FROM careers WHERE employee_id IN (SELECT id FROM employees WHERE employee_number LIKE '39%')");
        jdbc.update("DELETE FROM employees WHERE employee_number LIKE '39%'");
        reset(actors, authorization, audit);
    }

    @Test void createsA4KoreanRosterAndAuditsTheDownload() throws Exception {
        long employee = employee("39000101", "MALE", "서울특별시 중구 세종대로 1");
        jdbc.update("INSERT INTO careers(employee_id,start_date,end_date,institution,job_name,version) VALUES (?,?,?,?,?,0)",
            employee, LocalDate.of(2020, 1, 1), LocalDate.of(2025, 12, 31), "이전회사", "개발자");
        jdbc.update("""
            INSERT INTO employment_contracts(employee_id,actor_id,idempotency_key,contract_start,contract_end,
                work_location,weekly_work_minutes,agreed_monthly_wage) VALUES (?,?,?,?,?,?,?,?)
            """, employee, 55, "roster-contract", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 12, 31),
            "서울", 2400, 3000000);
        byte[] bytes = service.generate(employee);
        assertTrue(bytes.length > 10_000);
        String output = System.getenv("WORKER_ROSTER_PDF_OUTPUT");
        if (output != null && !output.isBlank()) {
            java.nio.file.Path path = java.nio.file.Path.of(output);
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, bytes);
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertEquals(1, document.getNumberOfPages());
            assertEquals(595.28f, document.getPage(0).getMediaBox().getWidth(), 0.1f);
            String text = new PDFTextStripper().getText(document);
            assertAll(() -> assertTrue(text.contains("근로자 명부")), () -> assertTrue(text.contains("홍길동")),
                () -> assertTrue(text.contains("서울특별시 중구")), () -> assertTrue(text.contains("이전회사")));
        }
        verify(authorization).checkCanReadRecord(55L, employee);
        verify(audit).viewed(55L, employee, "WORKER_ROSTER_PDF");
    }

    @Test void refusesLegacyRowsMissingLegalFields() {
        long employee = employee("39000102", "UNSPECIFIED", "");
        WorkerRosterIncompleteException failure = assertThrows(WorkerRosterIncompleteException.class,
            () -> service.generate(employee));
        assertEquals(java.util.List.of("gender", "address"), failure.missingFields());
        verify(audit, never()).viewed(anyLong(), anyLong(), anyString());
    }

    private long employee(String number, String gender, String address) {
        jdbc.update("""
            INSERT INTO employees(employee_number,employee_name,birth_date,gender,phone,address,hire_date,
                employment_type,workplace_id,department_id,position_name,foreign_worker)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,FALSE)
            """, number, "홍길동", LocalDate.of(1990,1,1), gender, "010-0000-0000", address,
            LocalDate.of(2026,1,2), "REGULAR", 1, 1, "개발자");
        return jdbc.queryForObject("SELECT id FROM employees WHERE employee_number=?", Long.class, number);
    }
}
