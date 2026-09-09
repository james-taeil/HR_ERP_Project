package com.jamestaeil.hrerp.platform.port;

public interface SensitiveValueCipher {
	byte[] encrypt(String plaintext);
}
