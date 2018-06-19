package common;

import java.util.Random;

import javax.xml.bind.DatatypeConverter;

public class Crypto {

	public static String base64encode(String text) {
		return DatatypeConverter.printBase64Binary(text.getBytes());
	}

	public static String base64decode(String text) {
		return new String(DatatypeConverter.parseBase64Binary(text));
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
		if(!ret.startsWith(user)) {
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
		try {
			if (message == null)
				return null;
			char[] mesg = message.toCharArray();
			Random r = new Random(seed);
			for (int i = 0; i < mesg.length; i++) {
				mesg[i] = (char) (mesg[i] ^ rndChar(r));
			}
			return new String(mesg);
		} catch (Exception e) {
			return null;
		}
	}

}