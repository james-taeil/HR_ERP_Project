package com.jamestaeil.hrerp.hr.record.infrastructure;

import java.time.LocalDate;
import jakarta.persistence.*;
import com.jamestaeil.hrerp.hr.record.domain.RecordData;
import com.jamestaeil.hrerp.hr.record.application.RecordViews.Entry;

public final class RecordEntities {
    private RecordEntities() {}

    @MappedSuperclass
    public abstract static class OwnedRecord {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        protected Long id;
        @Column(nullable = false, updatable = false)
        protected long employeeId;
        @Version protected long version;
        public abstract RecordData data();
        public abstract void replace(RecordData data);
        public Entry view() { return new Entry(id, version, data()); }
    }

    @Entity(name = "FamilyMember") @Table(name = "family_members")
    public static class FamilyMember extends OwnedRecord {
        @Column(name = "member_name", length = 100) private String name;
        @Column(name = "relationship_name", length = 50) private String relationship;
        private LocalDate birthDate;
        private boolean cohabiting, dependent, disabled, deductionEligible;
        protected FamilyMember() {}
        public RecordData.Family data() {
            return new RecordData.Family(name, relationship, birthDate, cohabiting, dependent, disabled, deductionEligible);
        }
        public void replace(RecordData input) {
            var d = (RecordData.Family) input;
            name = d.name(); relationship = d.relationship(); birthDate = d.birthDate();
            cohabiting = d.cohabiting(); dependent = d.dependent(); disabled = d.disabled();
            deductionEligible = d.deductionEligible();
        }
    }

    @Entity(name = "Education") @Table(name = "educations")
    public static class Education extends OwnedRecord {
        private LocalDate startDate, endDate;
        @Column(length = 200) private String institution;
        @Column(length = 200) private String major;
        private Long evidenceFileId;
        protected Education() {}
        public RecordData.Education data() {
            return new RecordData.Education(startDate, endDate, institution, major, evidenceFileId);
        }
        public void replace(RecordData input) {
            var d = (RecordData.Education) input;
            startDate = d.startDate(); endDate = d.endDate(); institution = d.institution();
            major = d.major(); evidenceFileId = d.evidenceFileId();
        }
    }

    @Entity(name = "Career") @Table(name = "careers")
    public static class Career extends OwnedRecord {
        private LocalDate startDate, endDate;
        @Column(length = 200) private String institution;
        @Column(name = "job_name", length = 200) private String job;
        private Long evidenceFileId;
        protected Career() {}
        public RecordData.Career data() {
            return new RecordData.Career(startDate, endDate, institution, job, evidenceFileId);
        }
        public void replace(RecordData input) {
            var d = (RecordData.Career) input;
            startDate = d.startDate(); endDate = d.endDate(); institution = d.institution();
            job = d.job(); evidenceFileId = d.evidenceFileId();
        }
    }

    @Entity(name = "Certification") @Table(name = "certifications")
    public static class Certification extends OwnedRecord {
        @Column(name = "certification_name", length = 200) private String name;
        @Column(length = 200) private String issuer;
        private LocalDate acquiredDate, expiresOn;
        protected Certification() {}
        public RecordData.Certification data() {
            return new RecordData.Certification(name, issuer, acquiredDate, expiresOn);
        }
        public void replace(RecordData input) {
            var d = (RecordData.Certification) input;
            name = d.name(); issuer = d.issuer(); acquiredDate = d.acquiredDate(); expiresOn = d.expiresOn();
        }
    }
}
