package com.jamestaeil.hrerp.platform.account;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordPolicyTest {
	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private final PasswordPolicy policy = new PasswordPolicy(12, 5);

	@Test
	void requiresTwelveCharacters() {
		assertThrows(IllegalArgumentException.class,
			() -> policy.validate("short-pass", List.of(), encoder));
		assertDoesNotThrow(
			() -> policy.validate("twelve-chars!", List.of(), encoder));
	}

	@Test
	void rejectsAnyOfTheFiveMostRecentPasswords() {
		List<String> history = List.of(
			encoder.encode("current-pass!"),
			encoder.encode("previous-01!"),
			encoder.encode("previous-02!"),
			encoder.encode("previous-03!"),
			encoder.encode("previous-04!"),
			encoder.encode("older-allowed!")
		);
		assertThrows(IllegalArgumentException.class,
			() -> policy.validate("previous-04!", history, encoder));
		assertDoesNotThrow(
			() -> policy.validate("older-allowed!", history, encoder));
	}

	@Test
	void appliesConfiguredMinimumLengthAndHistoryLimit() {
		PasswordPolicy configured = new PasswordPolicy(4, 1);
		List<String> history = List.of(encoder.encode("last"), encoder.encode("older"));

		assertThrows(IllegalArgumentException.class, () -> configured.validate("abc", List.of(), encoder));
		assertThrows(IllegalArgumentException.class, () -> configured.validate("last", history, encoder));
		assertDoesNotThrow(() -> configured.validate("older", history, encoder));
	}
}
