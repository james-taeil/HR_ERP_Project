package com.jamestaeil.hrerp.platform.account;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface PasswordHistoryRepository extends JpaRepository<PasswordHistoryEntity, Long> {
	List<PasswordHistoryEntity> findTop5ByAccountIdOrderByCreatedAtDescIdDesc(long accountId);
}
