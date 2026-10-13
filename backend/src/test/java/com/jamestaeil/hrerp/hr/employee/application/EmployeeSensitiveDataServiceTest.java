package com.jamestaeil.hrerp.hr.employee.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jamestaeil.hrerp.platform.port.AuthorizationChecker;
import com.jamestaeil.hrerp.platform.port.CurrentActorProvider;
import com.jamestaeil.hrerp.platform.port.SensitiveValueCipher;
import com.jamestaeil.hrerp.platform.port.RecordAudit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EmployeeSensitiveDataServiceTest {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    CurrentActorProvider actors = mock(CurrentActorProvider.class);
    AuthorizationChecker authorization = mock(AuthorizationChecker.class);
    SensitiveValueCipher cipher = mock(SensitiveValueCipher.class);
    RecordAudit audit = mock(RecordAudit.class);
    EmployeeSensitiveDataService service = new EmployeeSensitiveDataService(jdbc, actors, authorization, cipher, audit);

    @BeforeEach void setup() { when(actors.requireActorId()).thenReturn(7L); }

    @Test void encryptsBothValuesAndReturnsOnlyMasks() {
        when(jdbc.queryForObject(startsWith("SELECT COUNT"), eq(Integer.class), eq(11L))).thenReturn(1);
        when(cipher.encrypt("9001011234567")).thenReturn(new byte[]{1});
        when(cipher.encrypt("1234567890")).thenReturn(new byte[]{2});
        when(jdbc.query(contains("resident_number_mask"), any(org.springframework.jdbc.core.RowMapper.class), eq(11L)))
            .thenReturn(List.of());
        var result = service.save(11, "900101-1234567", "123-456-7890");
        assertEquals("900101-*******", result.residentRegistrationNumber());
        assertEquals("******7890", result.bankAccountNumber());
        verify(authorization).checkCanWriteRecord(7, 11);
        verify(audit).changed(eq(7L), eq(11L), eq("SENSITIVE_DATA"), eq(11L), isNull(), any());
        verify(jdbc).update(contains("employee_sensitive_data"), eq(11L), any(byte[].class),
            eq("900101-*******"), any(byte[].class), eq("******7890"));
    }

    @SuppressWarnings("unchecked")
    @Test void revealsWithDedicatedPermissionAndWritesAuditBeforeReturningPlaintext() {
        when(jdbc.query(contains("FROM employee_sensitive_data"), any(org.springframework.jdbc.core.RowMapper.class), eq(11L)))
            .thenAnswer(invocation -> {
                var mapper = (org.springframework.jdbc.core.RowMapper<Object>) invocation.getArgument(1);
                java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                when(rs.getBytes(1)).thenReturn(new byte[]{1}); when(rs.getString(2)).thenReturn("900101-*******");
                when(rs.getBytes(3)).thenReturn(new byte[]{2}); when(rs.getString(4)).thenReturn("******7890");
                return List.of(mapper.mapRow(rs, 0));
            });
        when(cipher.decrypt(any(byte[].class))).thenReturn("9001011234567", "1234567890");
        var result = service.reveal(11, "급여 계좌 확인");
        assertEquals("9001011234567", result.residentRegistrationNumber());
        verify(authorization).checkCanReadSensitiveRecord(7, 11);
        verify(jdbc).update(contains("sensitive_access_audits"), eq(7L), eq(11L), eq("급여 계좌 확인"));
    }
}
