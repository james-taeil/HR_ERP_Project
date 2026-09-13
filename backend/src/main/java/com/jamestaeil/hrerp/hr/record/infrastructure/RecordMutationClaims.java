package com.jamestaeil.hrerp.hr.record.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.jamestaeil.hrerp.hr.record.application.RecordConflictException;
import com.jamestaeil.hrerp.hr.record.application.RecordViews.Saved;
import com.jamestaeil.hrerp.hr.record.domain.RecordKind;

@Repository
public class RecordMutationClaims {
    private final JdbcClient jdbc;
    public RecordMutationClaims(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Saved claim(long actorId, String key, long employeeId, RecordKind kind, Long targetId) {
        long target = targetId == null ? 0 : targetId;
        jdbc.sql("""
            INSERT INTO record_mutation_requests (actor_id, idempotency_key, employee_id, record_kind, target_id)
            VALUES (:actor, :key, :employee, :kind, :target)
            ON DUPLICATE KEY UPDATE actor_id = actor_id
            """).param("actor", actorId).param("key", key).param("employee", employeeId)
            .param("kind", kind.name()).param("target", target).update();
        return jdbc.sql("""
            SELECT employee_id, record_kind, target_id, result_id, result_version
            FROM record_mutation_requests WHERE actor_id = :actor AND idempotency_key = :key FOR UPDATE
            """).param("actor", actorId).param("key", key).query((rs, row) -> {
                if (rs.getLong("employee_id") != employeeId || !rs.getString("record_kind").equals(kind.name())
                        || rs.getLong("target_id") != target) throw new RecordConflictException();
                long result = rs.getLong("result_id");
                return rs.wasNull() ? new Saved(0, 0) : new Saved(result, rs.getLong("result_version"));
            }).single();
    }

    public void complete(long actorId, String key, Saved result) {
        int changed = jdbc.sql("""
            UPDATE record_mutation_requests SET result_id = :id, result_version = :version
            WHERE actor_id = :actor AND idempotency_key = :key AND result_id IS NULL
            """).param("id", result.id()).param("version", result.version())
            .param("actor", actorId).param("key", key).update();
        if (changed != 1) throw new IllegalStateException("Record mutation claim unavailable");
    }
}
