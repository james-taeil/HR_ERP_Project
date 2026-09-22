package com.jamestaeil.hrerp.platform.account;

import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformAccountAccessSchedulerTest {
	@Mock AccountService accounts;

	@Test
	void convertsTheDayAfterTerminationToSeoulMidnight() {
		new PlatformAccountAccessScheduler(accounts).disableFrom(7L, LocalDate.of(2026, 9, 18));

		verify(accounts).scheduleDisableForEmployee(7L, Instant.parse("2026-09-17T15:00:00Z"));
	}
}
