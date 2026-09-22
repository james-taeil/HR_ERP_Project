package com.jamestaeil.hrerp.platform.account;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.jamestaeil.hrerp.platform.port.AccountAccessScheduler;

@Component
class PlatformAccountAccessScheduler implements AccountAccessScheduler {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final AccountService accounts;

	PlatformAccountAccessScheduler(AccountService accounts) {
		this.accounts = accounts;
	}

	@Override
	public void disableFrom(long employeeId, LocalDate date) {
		if (date == null) throw new IllegalArgumentException("비활성 적용일이 필요합니다.");
		accounts.scheduleDisableForEmployee(employeeId, date.atStartOfDay(SEOUL).toInstant());
	}
}
