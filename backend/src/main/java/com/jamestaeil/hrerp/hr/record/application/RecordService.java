package com.jamestaeil.hrerp.hr.record.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jamestaeil.hrerp.hr.record.domain.*;
import com.jamestaeil.hrerp.hr.record.infrastructure.*;
import com.jamestaeil.hrerp.platform.port.*;
import com.jamestaeil.hrerp.hr.record.application.RecordViews.*;

@Service
public class RecordService {
    private final RecordRepository records;
    private final RecordMutationClaims claims;
    private final CurrentActorProvider actors;
    private final AuthorizationChecker authorization;
    private final FileStorage files;
    private final RecordAudit audit;

    public RecordService(RecordRepository records, RecordMutationClaims claims, CurrentActorProvider actors,
                         AuthorizationChecker authorization, FileStorage files, RecordAudit audit) {
        this.records = records; this.claims = claims; this.actors = actors;
        this.authorization = authorization; this.files = files; this.audit = audit;
    }

    // Not readOnly: the access audit must be committed in this transaction.
    @Transactional
    public Card card(long employeeId) {
        long actor = authorize(employeeId, false);
        var employee = records.employee(employeeId);
        var card = new Card(employee, records.list(employeeId, RecordKind.FAMILY),
            records.list(employeeId, RecordKind.EDUCATION), records.list(employeeId, RecordKind.CAREER),
            records.list(employeeId, RecordKind.CERTIFICATION), List.of(), List.of());
        audit.viewed(actor, employeeId, "record");
        return card;
    }

    @Transactional
    public List<Entry> list(long employeeId, RecordKind kind) {
        long actor = authorize(employeeId, false);
        records.requireEmployee(employeeId);
        var entries = records.list(employeeId, kind);
        audit.viewed(actor, employeeId, kind.name());
        return entries;
    }

    @Transactional
    public Saved save(long employeeId, Long recordId, String key, Long version, RecordData data) {
        if (data == null || key == null || !key.matches("[A-Za-z0-9_-]{1,100}")
                || (recordId != null && (recordId <= 0 || version == null || version < 0))
                || (recordId == null && version != null)) throw new IllegalArgumentException("Invalid record request");
        long actor = authorize(employeeId, true);
        RecordKind kind = switch (data) {
            case RecordData.Family ignored -> RecordKind.FAMILY;
            case RecordData.Education ignored -> RecordKind.EDUCATION;
            case RecordData.Career ignored -> RecordKind.CAREER;
            case RecordData.Certification ignored -> RecordKind.CERTIFICATION;
        };
        records.requireEmployee(employeeId);
        Entry before = recordId == null ? null : records.get(employeeId, kind, recordId);
        Saved prior = claims.claim(actor, key, employeeId, kind, recordId);
        if (prior.id() != 0) return prior;
        Long fileId = switch (data) {
            case RecordData.Education education -> education.evidenceFileId();
            case RecordData.Career career -> career.evidenceFileId();
            default -> null;
        };
        if (fileId != null) files.requireUsableEvidence(actor, employeeId, fileId);
        Entry saved = records.save(employeeId, kind, recordId, version, data);
        audit.changed(actor, employeeId, kind.name(), saved.id(), before, saved);
        Saved result = new Saved(saved.id(), saved.version());
        claims.complete(actor, key, result);
        return result;
    }

    private long authorize(long employeeId, boolean write) {
        if (employeeId <= 0) throw new IllegalArgumentException("Invalid employee ID");
        long actor = actors.requireActorId();
        if (actor <= 0) throw new RecordAccessForbiddenException();
        if (write) authorization.checkCanWriteRecord(actor, employeeId);
        else authorization.checkCanReadRecord(actor, employeeId);
        return actor;
    }
}
