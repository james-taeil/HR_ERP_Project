package com.jamestaeil.hrerp.platform.account;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SessionRepository extends JpaRepository<SessionEntity, Long> {
	Optional<SessionEntity> findByTokenDigest(String tokenDigest);

	@Modifying(flushAutomatically = true)
	@Query("update SessionEntity session set session.revokedAt = :now "
		+ "where session.accountId = :accountId and session.revokedAt is null")
	int revokeAllByAccountId(@Param("accountId") long accountId, @Param("now") Instant now);
}
