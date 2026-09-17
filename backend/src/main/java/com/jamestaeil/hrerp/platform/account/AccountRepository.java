package com.jamestaeil.hrerp.platform.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface AccountRepository extends JpaRepository<AccountEntity, Long> {
	Optional<AccountEntity> findByUsername(String username);
}
