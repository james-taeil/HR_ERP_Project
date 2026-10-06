package com.jamestaeil.hrerp.hr.employee.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jamestaeil.hrerp.hr.employee.domain.EmploymentType;
import com.jamestaeil.hrerp.hr.employee.domain.Gender;
import com.jamestaeil.hrerp.platform.port.AuthorizationChecker;
import com.jamestaeil.hrerp.platform.port.CurrentActorProvider;
import com.jamestaeil.hrerp.platform.port.OrganizationReader;
import java.io.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EmployeeBulkService {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 1000;
    private static final List<String> HEADERS = List.of("성명","생년월일","성별","연락처","주소","입사일","고용형태","사업장ID","부서ID","직위","수습종료일","외국인여부","국적","체류자격","체류시작일","체류종료일","외국인등록번호");
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final AuthorizationChecker authorization;
    private final OrganizationReader organizations;
    private final RegisterEmployeeService registrations;
    private final ObjectMapper json = new ObjectMapper();

    public EmployeeBulkService(JdbcTemplate jdbc, CurrentActorProvider actors, AuthorizationChecker authorization,
            OrganizationReader organizations, RegisterEmployeeService registrations) {
        this.jdbc = jdbc; this.actors = actors; this.authorization = authorization;
        this.organizations = organizations; this.registrations = registrations;
    }
    public record RowError(int rowNumber, String code, String message) {}
    public record Validation(String validationToken, int totalRows, int validRows, int errorRows,
                             List<RowError> errors, Instant expiresAt) {}
    public record Registered(long employeeId, String employeeNumber) {}
    public record Confirmation(String validationToken, int registeredRows, List<Registered> employees) {}
    private record ParsedRow(int rowNumber, String name, LocalDate birthDate, Gender gender, String phone, String address, LocalDate hireDate,
        EmploymentType employmentType, long workplaceId, long departmentId, String position,
        LocalDate probationEndDate, boolean foreignWorker, String nationality, String visaType,
        LocalDate stayFrom, LocalDate stayUntil, String alienRegistrationNumber) {}
    private record Parsed(List<ParsedRow> rows, List<RowError> errors, int totalRows) {}

    @Transactional
    public Validation validate(MultipartFile file) {
        long actor = requireActor(); authorization.checkCanRegisterEmployee();
        byte[] bytes = bytes(file); Parsed parsed = parse(bytes, true);
        String token = UUID.randomUUID().toString(); Instant expires = Instant.now().plus(Duration.ofMinutes(30));
        int errorRows = (int) parsed.errors().stream().map(RowError::rowNumber).distinct().count();
        jdbc.update("""
            INSERT INTO employee_bulk_validations
                (validation_token, actor_id, file_sha256, total_rows, error_rows, expires_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """, token, actor, sha256(bytes), parsed.totalRows(), errorRows, expires);
        return new Validation(token, parsed.totalRows(), parsed.totalRows() - errorRows,
            errorRows, parsed.errors(), expires);
    }

    @Transactional
    public Confirmation confirm(String token, MultipartFile file) {
        if (token == null || !token.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid validation token");
        long actor = requireActor(); authorization.checkCanRegisterEmployee();
        List<Claim> claims = jdbc.query("""
            SELECT actor_id, file_sha256, total_rows, error_rows, expires_at, result_json
            FROM employee_bulk_validations WHERE validation_token=? FOR UPDATE
            """, (rs, row) -> new Claim(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                rs.getTimestamp(5).toInstant(), rs.getString(6)), token);
        if (claims.isEmpty()) throw new EmployeeBulkConflictException("Validation token was not found");
        Claim claim = claims.getFirst();
        if (claim.actorId() != actor || claim.expiresAt().isBefore(Instant.now()) || claim.errorRows() != 0)
            throw new EmployeeBulkConflictException("Validation cannot be confirmed");
        if (claim.resultJson() != null) return readResult(claim.resultJson());
        byte[] bytes = bytes(file);
        if (!MessageDigest.isEqual(claim.fileHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                sha256(bytes).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new EmployeeBulkConflictException("Uploaded file differs from validated file");
        Parsed parsed = parse(bytes, true);
        if (!parsed.errors().isEmpty() || parsed.totalRows() != claim.totalRows())
            throw new EmployeeBulkConflictException("File no longer passes validation");
        List<Registered> saved = new ArrayList<>(parsed.rows().size());
        for (ParsedRow row : parsed.rows()) {
            RegisterEmployeeResult result = registrations.register(command(token, row));
            saved.add(new Registered(result.employeeId(), result.employeeNumber()));
        }
        Confirmation result = new Confirmation(token, saved.size(), List.copyOf(saved));
        String resultJson = writeResult(result);
        int updated = jdbc.update("""
            UPDATE employee_bulk_validations SET confirmed_at=CURRENT_TIMESTAMP(6), result_json=?
            WHERE validation_token=? AND confirmed_at IS NULL
            """, resultJson, token);
        if (updated != 1) throw new EmployeeBulkConflictException("Bulk confirmation conflict");
        return result;
    }

    private Parsed parse(byte[] bytes, boolean validateOrganization) {
        List<ParsedRow> rows = new ArrayList<>(); List<RowError> errors = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT); int totalRows = 0;
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("사원등록");
            if (sheet == null) throw new IllegalArgumentException("Missing employee sheet");
            Row header = sheet.getRow(0);
            if (header == null) throw new IllegalArgumentException("Missing header row");
            for (int i = 0; i < HEADERS.size(); i++) if (!HEADERS.get(i).equals(formatter.formatCellValue(header.getCell(i))))
                throw new IllegalArgumentException("Invalid template header");
            int last = sheet.getLastRowNum();
            for (int index = 1; index <= last; index++) {
                Row source = sheet.getRow(index); if (source == null || blank(source, formatter)) continue;
                totalRows++;
                if (totalRows > MAX_ROWS) throw new IllegalArgumentException("Bulk file exceeds 1000 rows");
                int rowNumber = index + 1;
                if (hasFormula(source)) { errors.add(new RowError(rowNumber, "FORMULA_NOT_ALLOWED", "Formula cells are not allowed")); continue; }
                try {
                    ParsedRow row = map(source, rowNumber, formatter);
                    if (validateOrganization) organizations.requireActiveDepartment(row.workplaceId(), row.departmentId());
                    rows.add(row);
                } catch (RuntimeException failure) {
                    errors.add(new RowError(rowNumber, "INVALID_ROW", "Row data is invalid"));
                }
            }
        } catch (EmployeeBulkConflictException | IllegalArgumentException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalArgumentException("Invalid XLSX file", failure); }
        if (totalRows == 0) throw new IllegalArgumentException("Bulk file has no rows");
        Map<String, Integer> seen = new HashMap<>();
        for (ParsedRow row : rows) {
            String key = canonical(row);
            Integer prior = seen.putIfAbsent(key, row.rowNumber());
            if (prior != null) errors.add(new RowError(row.rowNumber(), "DUPLICATE_ROW", "Row duplicates row " + prior));
        }
        return new Parsed(List.copyOf(rows), List.copyOf(errors), totalRows);
    }

    private static ParsedRow map(Row row, int number, DataFormatter f) {
        String name = required(row, 0, f, 100); LocalDate birth = date(row, 1, true, f);
        Gender gender = Gender.valueOf(required(row, 2, f, 20));
        if (gender == Gender.UNSPECIFIED) throw new IllegalArgumentException();
        String phone = required(row, 3, f, 30); String address = required(row, 4, f, 500);
        LocalDate hire = date(row, 5, true, f);
        EmploymentType type = EmploymentType.valueOf(required(row, 6, f, 30));
        long workplace = positiveLong(row, 7, f); long department = positiveLong(row, 8, f);
        String position = required(row, 9, f, 100); LocalDate probation = date(row, 10, false, f);
        boolean foreign = bool(row, 11, f); String nationality = optional(row, 12, f);
        String visa = optional(row, 13, f); LocalDate stayFrom = date(row, 14, false, f);
        LocalDate stayUntil = date(row, 15, false, f); String registration = optional(row, 16, f);
        if (probation != null && probation.isBefore(hire)) throw new IllegalArgumentException();
        if (foreign && (blank(nationality) || blank(visa) || stayFrom == null || stayUntil == null
                || stayUntil.isBefore(stayFrom) || registration == null
                || registration.replaceAll("[^0-9]", "").length() != 13)) throw new IllegalArgumentException();
        if (!foreign && (!blank(nationality) || !blank(visa) || stayFrom != null || stayUntil != null || !blank(registration)))
            throw new IllegalArgumentException();
        return new ParsedRow(number, name, birth, gender, phone, address, hire, type, workplace, department, position,
            probation, foreign, nationality, visa, stayFrom, stayUntil, registration);
    }
    private static RegisterEmployeeCommand command(String token, ParsedRow r) {
        return new RegisterEmployeeCommand("bulk:" + token + ":" + r.rowNumber(), r.name(), r.birthDate(), r.gender(), r.phone(), r.address(),
            r.hireDate(), r.employmentType(), r.workplaceId(), r.departmentId(), r.position(), r.probationEndDate(),
            r.foreignWorker(), r.nationality(), r.visaType(), r.stayFrom(), r.stayUntil(), r.alienRegistrationNumber());
    }
    private byte[] bytes(MultipartFile file) {
        try {
            if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES || file.getOriginalFilename() == null
                    || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
                throw new IllegalArgumentException("A valid XLSX file is required");
            byte[] bytes = file.getBytes();
            if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') throw new IllegalArgumentException("Invalid XLSX signature");
            return bytes;
        } catch (IOException failure) { throw new IllegalArgumentException("Could not read XLSX file", failure); }
    }
    private long requireActor() { long actor = actors.requireActorId(); if (actor <= 0) throw new IllegalArgumentException(); return actor; }
    private static boolean hasFormula(Row row) { for (int i=0;i<HEADERS.size();i++) { Cell c=row.getCell(i); if (c!=null && c.getCellType()==CellType.FORMULA) return true; } return false; }
    private static boolean blank(Row row, DataFormatter f) { for (int i=0;i<HEADERS.size();i++) if (!f.formatCellValue(row.getCell(i)).isBlank()) return false; return true; }
    private static String required(Row r,int i,DataFormatter f,int max) { String v=optional(r,i,f); if(blank(v)||v.length()>max) throw new IllegalArgumentException(); return v; }
    private static String optional(Row r,int i,DataFormatter f) { Cell c=r.getCell(i); return c==null?null:f.formatCellValue(c).trim(); }
    private static LocalDate date(Row r,int i,boolean required,DataFormatter f) { Cell c=r.getCell(i); if(c==null||c.getCellType()==CellType.BLANK){if(required)throw new IllegalArgumentException();return null;} if(c.getCellType()==CellType.NUMERIC&&DateUtil.isCellDateFormatted(c)) return c.getLocalDateTimeCellValue().toLocalDate(); String v=f.formatCellValue(c).trim(); if(v.isEmpty()&&!required)return null; return LocalDate.parse(v); }
    private static long positiveLong(Row r,int i,DataFormatter f) { String v=required(r,i,f,20).replace(",",""); long n=Long.parseLong(v.replaceAll("\\.0$","")); if(n<=0)throw new IllegalArgumentException(); return n; }
    private static boolean bool(Row r,int i,DataFormatter f) { String v=required(r,i,f,5); if("TRUE".equalsIgnoreCase(v))return true; if("FALSE".equalsIgnoreCase(v))return false; throw new IllegalArgumentException(); }
    private static boolean blank(String v) { return v==null||v.isBlank(); }
    private static String canonical(ParsedRow r) { return r.name()+"|"+r.birthDate()+"|"+r.gender()+"|"+r.phone()+"|"+r.address()+"|"+r.hireDate()+"|"+r.employmentType()+"|"+r.workplaceId()+"|"+r.departmentId()+"|"+r.position()+"|"+r.foreignWorker(); }
    private static String sha256(byte[] bytes) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch(Exception e){throw new IllegalStateException(e);} }
    private String writeResult(Confirmation result) { try{return json.writeValueAsString(result);}catch(JsonProcessingException e){throw new IllegalStateException(e);} }
    private Confirmation readResult(String value) { try{return json.readValue(value, Confirmation.class);}catch(JsonProcessingException e){throw new IllegalStateException(e);} }
    private record Claim(long actorId,String fileHash,int totalRows,int errorRows,Instant expiresAt,String resultJson) {}
}
