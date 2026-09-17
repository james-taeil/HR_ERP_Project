package com.jamestaeil.hrerp.platform.account;

import org.springframework.data.jpa.repository.JpaRepository;

interface LoginHistoryRepository extends JpaRepository<LoginHistoryEntity, Long> {}
