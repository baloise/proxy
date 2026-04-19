package common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class CryptoTest {

	@Test
	void userRoundtrip() {
		String plain = "super-secret-password!42";
		String encrypted = Crypto.userEncrypt(plain);
		assertNotEquals(plain, encrypted);
		assertEquals(plain, Crypto.userDecrypt(encrypted));
	}

	@Test
	void base64Roundtrip() {
		String s = "Hello, World! äöü 中";
		assertEquals(s, Crypto.base64decode(Crypto.base64encode(s)));
	}

	@Test
	void seededXorRoundtrip() {
		String msg = "routing-is-fun";
		long seed = 1234567L;
		assertEquals(msg, Crypto.decrypt(Crypto.encrypt(msg, seed), seed));
	}
}
