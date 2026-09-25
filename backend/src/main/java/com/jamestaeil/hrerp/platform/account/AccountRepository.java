package com.jamestaeil.hrerp.platform.account;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AccountRepository extends JpaRepository<AccountEntity, Long> {
	Optional<AccountEntity> findByUsername(String username);
	Optional<AccountEntity> findByEmployeeId(long employeeId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from AccountEntity account where account.username = :username")
	Optional<AccountEntity> findLockedByUsername(@Param("username") String username);
}
