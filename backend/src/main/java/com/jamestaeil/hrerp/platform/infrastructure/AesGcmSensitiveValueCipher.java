package com.jamestaeil.hrerp.platform.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jamestaeil.hrerp.platform.port.SensitiveValueCipher;

@Component
public final class AesGcmSensitiveValueCipher implements SensitiveValueCipher {

	private static final int NONCE_LENGTH = 12;
	private final String encodedKey;
	private final SecureRandom secureRandom = new SecureRandom();

	public AesGcmSensitiveValueCipher(@Value("${app.security.sensitive-key:}") String encodedKey) {
		this.encodedKey = encodedKey;
	}

	@Override
	public byte[] encrypt(String plaintext) {
		if (plaintext == null || plaintext.isBlank()) {
			throw new IllegalArgumentException("Registration number is required");
		}
		try {
			byte[] key = Base64.getDecoder().decode(encodedKey);
			if (key.length != 32) throw new IllegalStateException("A 256-bit sensitive data key is required");
			byte[] nonce = new byte[NONCE_LENGTH];
			secureRandom.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
			return ByteBuffer.allocate(nonce.length + cipher.getOutputSize(plaintext.length()))
				.put(nonce)
				.put(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)))
				.array();
		} catch (IllegalArgumentException | GeneralSecurityException exception) {
			throw new IllegalStateException("Sensitive value encryption failed", exception);
		}
	}
}
