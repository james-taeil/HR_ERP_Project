package com.jamestaeil.hrerp.hr.record;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.jamestaeil.hrerp.hr.record.application.*;
import com.jamestaeil.hrerp.hr.record.application.RecordViews.*;
import com.jamestaeil.hrerp.hr.record.domain.*;
import com.jamestaeil.hrerp.hr.record.infrastructure.*;
import com.jamestaeil.hrerp.platform.port.*;

class RecordServiceTest {
    private final RecordRepository records = mock(RecordRepository.class);
    private final RecordMutationClaims claims = mock(RecordMutationClaims.class);
    private final CurrentActorProvider actors = mock(CurrentActorProvider.class);
    private final AuthorizationChecker authorization = mock(AuthorizationChecker.class);
    private final FileStorage files = mock(FileStorage.class);
    private final RecordAudit audit = mock(RecordAudit.class);
    private final RecordService service = new RecordService(records, claims, actors, authorization, files, audit);
    private final RecordData.Family data = new RecordData.Family("테스트", "자녀", LocalDate.of(2020, 1, 1), true, true, false, false);

    @BeforeEach void setup() {
        when(actors.requireActorId()).thenReturn(9L);
        when(claims.claim(9L, "key", 1L, RecordKind.FAMILY, null)).thenReturn(new Saved(0, 0));
    }

    @Test void savesAndAuditsBeforeCompletingClaim() {
        var entry = new Entry(2, 0, data);
        when(records.save(1, RecordKind.FAMILY, null, null, data)).thenReturn(entry);
        assertEquals(new Saved(2, 0), service.save(1, null, "key", null, data));
        var order = inOrder(authorization, records, claims, audit);
        order.verify(authorization).checkCanWriteRecord(9, 1);
        order.verify(records).requireEmployee(1);
        order.verify(claims).claim(9, "key", 1, RecordKind.FAMILY, null);
        order.verify(records).save(1, RecordKind.FAMILY, null, null, data);
        order.verify(audit).changed(9, 1, "FAMILY", 2, null, entry);
        order.verify(claims).complete(9, "key", new Saved(2, 0));
    }

    @Test void repeatReturnsOriginalResultWithoutWritingAgain() {
        when(claims.claim(9, "key", 1, RecordKind.FAMILY, null)).thenReturn(new Saved(2, 0));
        assertEquals(new Saved(2, 0), service.save(1, null, "key", null, data));
        verify(records, never()).save(anyLong(), any(), any(), any(), any());
        verifyNoInteractions(files, audit);
    }

    @Test void forbiddenAccessNeverReadsOrWritesPrivateData() {
        doThrow(new RecordAccessForbiddenException()).when(authorization).checkCanReadRecord(9, 1);
        doThrow(new RecordAccessForbiddenException()).when(authorization).checkCanWriteRecord(9, 1);
        assertThrows(RecordAccessForbiddenException.class, () -> service.card(1));
        assertThrows(RecordAccessForbiddenException.class, () -> service.list(1, RecordKind.FAMILY));
        assertThrows(RecordAccessForbiddenException.class, () -> service.save(1, null, "key", null, data));
        verifyNoInteractions(records, claims, files, audit);
    }

    @Test void missingPlatformIdentityFailsClosed() {
        when(actors.requireActorId()).thenThrow(new PlatformIntegrationUnavailableException());
        assertThrows(PlatformIntegrationUnavailableException.class, () -> service.card(1));
        verifyNoInteractions(records, claims, files, audit);
    }

    @Test void wrongOwnerCannotClaimOrModifyChild() {
        when(records.get(1, RecordKind.FAMILY, 5)).thenThrow(new RecordNotFoundException());
        assertThrows(RecordNotFoundException.class, () -> service.save(1, 5L, "key", 0L, data));
        verifyNoInteractions(claims, audit, files);
    }

    @Test void auditFailureDoesNotCompleteTheClaim() {
        var entry = new Entry(2, 0, data);
        when(records.save(1, RecordKind.FAMILY, null, null, data)).thenReturn(entry);
        doThrow(new PlatformIntegrationUnavailableException()).when(audit).changed(9, 1, "FAMILY", 2, null, entry);
        assertThrows(PlatformIntegrationUnavailableException.class, () -> service.save(1, null, "key", null, data));
        verify(claims, never()).complete(anyLong(), any(), any());
    }

    @Test void evidenceMustPassPlatformValidationBeforeSave() {
        var education = new RecordData.Education(LocalDate.of(2020, 1, 1), null, "테스트 학교", null, 12L);
        when(claims.claim(9, "key", 1, RecordKind.EDUCATION, null)).thenReturn(new Saved(0, 0));
        doThrow(new RecordAccessForbiddenException()).when(files).requireUsableEvidence(9, 1, 12);
        assertThrows(RecordAccessForbiddenException.class, () -> service.save(1, null, "key", null, education));
        verify(records, never()).save(anyLong(), any(), any(), any(), any());
        verifyNoInteractions(audit);
    }

    @Test void validatesVersionAndKeyBeforeAnyWrite() {
        assertThrows(IllegalArgumentException.class, () -> service.save(1, 2L, "key", null, data));
        assertThrows(IllegalArgumentException.class, () -> service.save(1, null, "key", 0L, data));
        assertThrows(IllegalArgumentException.class, () -> service.save(1, null, "한글", null, data));
        verifyNoInteractions(records, claims, audit);
    }

    @Test void cardReadsExactlyFourSectionsAndAuditsAccess() {
        for (RecordKind kind : RecordKind.values()) when(records.list(1, kind)).thenReturn(List.of());
        var card = service.card(1);
        assertTrue(card.appointments().isEmpty()); assertTrue(card.contracts().isEmpty());
        verify(records).employee(1);
        for (RecordKind kind : RecordKind.values()) verify(records).list(1, kind);
        verifyNoMoreInteractions(records);
        verify(audit).viewed(9, 1, "record");
    }
}
