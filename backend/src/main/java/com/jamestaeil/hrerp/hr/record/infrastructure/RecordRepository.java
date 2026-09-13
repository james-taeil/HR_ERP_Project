package com.jamestaeil.hrerp.hr.record.infrastructure;

import java.util.List;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import com.jamestaeil.hrerp.hr.record.application.RecordNotFoundException;
import com.jamestaeil.hrerp.hr.record.application.RecordConflictException;
import com.jamestaeil.hrerp.hr.record.application.RecordViews.*;
import com.jamestaeil.hrerp.hr.record.domain.RecordData;
import com.jamestaeil.hrerp.hr.record.domain.RecordKind;
import com.jamestaeil.hrerp.hr.record.infrastructure.RecordEntities.*;

@Repository
public class RecordRepository {
    private final EntityManager em;
    public RecordRepository(EntityManager em) { this.em = em; }

    public EmployeeSummary employee(long id) {
        return em.createQuery("""
            select new com.jamestaeil.hrerp.hr.record.application.RecordViews$EmployeeSummary(
                e.id, e.employeeNumber, e.employeeName, e.birthDate, e.phone, e.hireDate,
                e.employmentType, e.workplaceId, e.departmentId, e.positionName,
                e.probationEndDate, e.foreignWorker)
            from EmployeeEntity e where e.id = :id
            """, EmployeeSummary.class).setParameter("id", id).getResultStream().findFirst()
            .orElseThrow(RecordNotFoundException::new);
    }

    public void requireEmployee(long id) {
        if (em.createQuery("select e.id from EmployeeEntity e where e.id = :id", Long.class)
                .setParameter("id", id).getResultList().isEmpty()) throw new RecordNotFoundException();
    }

    public List<Entry> list(long employeeId, RecordKind kind) {
        Class<? extends OwnedRecord> type = type(kind);
        return em.createQuery("select r from " + type.getSimpleName()
                + " r where r.employeeId = :employeeId order by r.id", type)
            .setParameter("employeeId", employeeId).getResultList().stream().map(OwnedRecord::view).toList();
    }

    public Entry get(long employeeId, RecordKind kind, long id) {
        return owned(employeeId, kind, id).view();
    }

    public Entry save(long employeeId, RecordKind kind, Long id, Long expectedVersion, RecordData data) {
        OwnedRecord entity;
        if (id == null) {
            entity = switch (kind) {
                case FAMILY -> new FamilyMember();
                case EDUCATION -> new Education();
                case CAREER -> new Career();
                case CERTIFICATION -> new Certification();
            };
            entity.employeeId = employeeId;
            entity.replace(data);
            em.persist(entity);
        } else {
            entity = owned(employeeId, kind, id);
            if (expectedVersion == null || entity.version != expectedVersion) throw new RecordConflictException();
            entity.replace(data);
        }
        em.flush();
        return entity.view();
    }

    private OwnedRecord owned(long employeeId, RecordKind kind, long id) {
        Class<? extends OwnedRecord> type = type(kind);
        return em.createQuery("select r from " + type.getSimpleName()
                + " r where r.id = :id and r.employeeId = :employeeId", type)
            .setParameter("id", id).setParameter("employeeId", employeeId).getResultStream().findFirst()
            .orElseThrow(RecordNotFoundException::new);
    }

    private static Class<? extends OwnedRecord> type(RecordKind kind) {
        return switch (kind) {
            case FAMILY -> FamilyMember.class;
            case EDUCATION -> Education.class;
            case CAREER -> Career.class;
            case CERTIFICATION -> Certification.class;
        };
    }
}
