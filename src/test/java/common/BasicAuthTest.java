package common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BasicAuthTest {

	private String priorEncrypted;

	@BeforeEach
	void savePrior() {
		priorEncrypted = Password.node().get("password", null);
	}

	@AfterEach
	void restorePrior() {
		if (priorEncrypted == null) {
			Password.remove();
		} else {
			Password.node().put("password", priorEncrypted);
		}
	}

	@Test
	void basicAuthReturnsBase64UserColonPassword() {
		Password.set("secret42");
		String token = BasicAuth.get();
		assertNotNull(token);
		// Base64 decode and verify the payload is "<user>:secret42".
		String decoded = new String(Base64.getDecoder().decode(token));
		int colon = decoded.indexOf(':');
		assertTrue(colon > 0, "decoded header must contain ':' - got: " + decoded);
		assertEquals(System.getProperty("user.name"), decoded.substring(0, colon));
		assertEquals("secret42", decoded.substring(colon + 1));
	}
}
