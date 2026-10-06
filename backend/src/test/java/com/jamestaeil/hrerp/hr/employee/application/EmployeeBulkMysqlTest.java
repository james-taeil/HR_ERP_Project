package com.jamestaeil.hrerp.hr.employee.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jamestaeil.hrerp.hr.employee.domain.DepartmentCode;
import com.jamestaeil.hrerp.platform.port.*;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "HR_RECORD_MYSQL_TEST_URL", matches = ".+/hr_record_test(?:\\?.*)?$")
class EmployeeBulkMysqlTest {
    private static final String[] HEADERS = {"성명","생년월일","성별","연락처","주소","입사일","고용형태","사업장ID","부서ID","직위","수습종료일","외국인여부","국적","체류자격","체류시작일","체류종료일","외국인등록번호"};
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("HR_RECORD_MYSQL_TEST_URL"));
        p.add("spring.datasource.username", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_USER", "root"));
        p.add("spring.datasource.password", () -> System.getenv().getOrDefault("HR_RECORD_MYSQL_TEST_PASSWORD", ""));
    }
    @Autowired EmployeeBulkService service;
    @Autowired DataSource source;
    @MockitoBean CurrentActorProvider actors;
    @MockitoBean AuthorizationChecker authorization;
    @MockitoBean OrganizationReader organizations;
    @MockitoBean SensitiveValueCipher cipher;
    private JdbcTemplate jdbc;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(source);
        when(actors.requireActorId()).thenReturn(9L);
        when(organizations.requireActiveDepartment(anyLong(), anyLong())).thenReturn(new DepartmentCode("01"));
        when(cipher.encrypt(anyString())).thenReturn(new byte[] {1, 2, 3});
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM foreign_worker_profiles WHERE employee_id IN (SELECT id FROM employees WHERE employee_name LIKE 'BULK-%')");
        jdbc.update("DELETE FROM employee_events WHERE employee_id IN (SELECT id FROM employees WHERE employee_name LIKE 'BULK-%')");
        jdbc.update("DELETE FROM onboarding_checklists WHERE employee_id IN (SELECT id FROM employees WHERE employee_name LIKE 'BULK-%')");
        jdbc.update("DELETE FROM employee_registration_requests WHERE idempotency_key LIKE 'bulk:%'");
        jdbc.update("DELETE FROM employees WHERE employee_name LIKE 'BULK-%'");
        jdbc.update("DELETE FROM employee_bulk_validations WHERE actor_id=9");
        reset(actors, authorization, organizations, cipher);
    }

    @Test void validatesAllRowsAndDoesNotPersistSourceValues() throws Exception {
        byte[] file = workbook(2, false, false);
        EmployeeBulkService.Validation validation = service.validate(upload(file));
        assertEquals(2, validation.totalRows()); assertEquals(0, validation.errorRows());
        String stored = jdbc.queryForObject("SELECT CAST(result_json AS CHAR) FROM employee_bulk_validations WHERE validation_token=?",
            String.class, validation.validationToken());
        assertNull(stored);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE employee_name LIKE 'BULK-%'", Integer.class));
    }

    @Test void reportsFormulaAndDuplicateRowsAndRefusesConfirmation() throws Exception {
        byte[] file = workbook(3, true, true);
        var validation = service.validate(upload(file));
        assertEquals(3, validation.totalRows()); assertTrue(validation.errorRows() > 0);
        assertTrue(validation.errors().stream().anyMatch(e -> e.code().equals("FORMULA_NOT_ALLOWED")));
        assertTrue(validation.errors().stream().anyMatch(e -> e.code().equals("DUPLICATE_ROW")));
        assertThrows(EmployeeBulkConflictException.class,
            () -> service.confirm(validation.validationToken(), upload(file)));
    }

    @Test void confirmsAtomicallyAndReplayReturnsFirstResult() throws Exception {
        byte[] file = workbook(2, false, false);
        var validation = service.validate(upload(file));
        var first = service.confirm(validation.validationToken(), upload(file));
        var replay = service.confirm(validation.validationToken(), upload(file));
        assertEquals(2, first.registeredRows()); assertEquals(first, replay);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE employee_name LIKE 'BULK-%'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM employee_events WHERE employee_id IN (SELECT id FROM employees WHERE employee_name LIKE 'BULK-%')", Integer.class));
    }

    @Test void rollsBackEveryRowWhenEncryptionFails() throws Exception {
        byte[] file = foreignWorkbook();
        var validation = service.validate(upload(file));
        doThrow(new IllegalStateException("cipher unavailable")).when(cipher).encrypt(anyString());
        assertThrows(IllegalStateException.class, () -> service.confirm(validation.validationToken(), upload(file)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE employee_name LIKE 'BULK-%'", Integer.class));
    }

    @Test void rejectsMoreThanOneThousandRowsAndChangedConfirmationFile() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.validate(upload(workbook(1001, false, false))));
        byte[] first = workbook(1, false, false);
        var validation = service.validate(upload(first));
        assertThrows(EmployeeBulkConflictException.class,
            () -> service.confirm(validation.validationToken(), upload(workbook(2, false, false))));
    }

    @Test void concurrentConfirmationReturnsOneCommittedResult() throws Exception {
        byte[] file = workbook(2, false, false);
        var validation = service.validate(upload(file));
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<EmployeeBulkService.Confirmation> first = executor.submit(
                () -> service.confirm(validation.validationToken(), upload(file)));
            Future<EmployeeBulkService.Confirmation> second = executor.submit(
                () -> service.confirm(validation.validationToken(), upload(file)));
            assertEquals(first.get(), second.get());
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE employee_name LIKE 'BULK-%'", Integer.class));
    }

    @Test void validatesAndConfirmsOneThousandRowsWithinSixtySeconds() throws Exception {
        byte[] file = workbook(1000, false, false);
        assertTimeout(Duration.ofSeconds(60), () -> {
            var validation = service.validate(upload(file));
            var confirmation = service.confirm(validation.validationToken(), upload(file));
            assertEquals(1000, confirmation.registeredRows());
        });
    }

    private MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "employees.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }
    private byte[] workbook(int count, boolean formula, boolean duplicate) throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("사원등록"); Row header = sheet.createRow(0);
            for (int i=0;i<HEADERS.length;i++) header.createCell(i).setCellValue(HEADERS[i]);
            for (int i=1;i<=count;i++) {
                Row row=sheet.createRow(i); int value=duplicate?1:i;
                row.createCell(0).setCellValue("BULK-"+value);
                row.createCell(1).setCellValue("1990-01-01"); row.createCell(2).setCellValue("MALE");
                row.createCell(3).setCellValue("010-0000-"+String.format("%04d", value)); row.createCell(4).setCellValue("서울특별시 중구");
                row.createCell(5).setCellValue("2026-01-01"); row.createCell(6).setCellValue("REGULAR");
                row.createCell(7).setCellValue(1); row.createCell(8).setCellValue(1); row.createCell(9).setCellValue("사원");
                row.createCell(11).setCellValue("FALSE");
            }
            if (formula) sheet.getRow(1).createCell(0).setCellFormula("\"BULK-FORMULA\"");
            wb.write(out); return out.toByteArray();
        }
    }
    private byte[] foreignWorkbook() throws Exception {
        byte[] bytes = workbook(1, false, false);
        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Row row=wb.getSheet("사원등록").getRow(1); row.getCell(11).setCellValue("TRUE");
            row.createCell(12).setCellValue("대한민국"); row.createCell(13).setCellValue("F-2");
            row.createCell(14).setCellValue("2026-01-01"); row.createCell(15).setCellValue("2027-01-01");
            row.createCell(16).setCellValue("900101-1234567"); wb.write(out); return out.toByteArray();
        }
    }
}
