package com.jamestaeil.hrerp.platform.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class AesGcmSensitiveValueCipherTest {
	@Test
	void encryptsWithoutLeavingPlaintextBytes() {
		String key = Base64.getEncoder().encodeToString(new byte[32]);
		byte[] encrypted = new AesGcmSensitiveValueCipher(key).encrypt("123456-1234567");
		assertFalse(new String(encrypted, StandardCharsets.UTF_8).contains("123456"));
	}

	@Test
	void rejectsMissingKey() {
		assertThrows(IllegalStateException.class,
			() -> new AesGcmSensitiveValueCipher("").encrypt("123456-1234567"));
	}
}
