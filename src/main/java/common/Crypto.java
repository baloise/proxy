package common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;

/**
 * User-scoped XOR obfuscation of the stored proxy password.
 *
 * <p><b>Not real cryptography.</b> The goal is only to prevent casual snooping of
 * the {@code com/baloise/proxy/password} Java Preferences key by another process
 * with read access. Anyone who can run code as this OS user can trivially recover
 * the plaintext.
 *
 * <p>Uses UTF-8 consistently so a password set on Windows and (theoretically) read
 * on another OS with non-ASCII characters still roundtrips.
 */
public final class Crypto {

	private Crypto() {}

	public static String base64encode(String text) {
		return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
	}

	public static String base64decode(String text) {
		return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
	}

	public static String userEncrypt(String message) {
		String user = System.getProperty("user.name");
		long seed = user.hashCode();
		return encrypt(user + message, seed);
	}

	public static String userDecrypt(String message) {
		String user = System.getProperty("user.name");
		long seed = user.hashCode();
		String ret = decrypt(message, seed);
		if (ret == null || !ret.startsWith(user)) {
			throw new IllegalStateException("Can not decrypt " + message);
		}
		return ret.substring(user.length());
	}

	public static String encrypt(String message, long seed) {
		return base64encode(xorMessage(message, seed));
	}

	public static String decrypt(String message, long seed) {
		return xorMessage(base64decode(message), seed);
	}

	private static char rndChar(Random r) {
		int rnd = (int) (r.nextDouble() * 52);
		char base = (rnd < 26) ? 'A' : 'a';
		return (char) (base + rnd % 26);
	}

	public static String xorMessage(String message, long seed) {
		if (message == null) return null;
		char[] mesg = message.toCharArray();
		Random r = new Random(seed);
		for (int i = 0; i < mesg.length; i++) {
			mesg[i] = (char) (((char) mesg[i]) ^ ((char) rndChar(r)));
		}
		return new String(mesg);
	}
}
