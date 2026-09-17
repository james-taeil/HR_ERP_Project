package com.jamestaeil.hrerp.platform.account;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordPolicyTest {
	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

	@Test
	void requiresTwelveCharacters() {
		assertThrows(IllegalArgumentException.class,
			() -> PasswordPolicy.validate("short-pass", List.of(), encoder));
		assertDoesNotThrow(
			() -> PasswordPolicy.validate("twelve-chars!", List.of(), encoder));
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
			() -> PasswordPolicy.validate("previous-04!", history, encoder));
		assertDoesNotThrow(
			() -> PasswordPolicy.validate("older-allowed!", history, encoder));
	}
}
