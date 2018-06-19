package common;

import java.util.Base64;

public class BasicAuth {
	public static String get() {
		return new String(Base64.getEncoder().encodeToString((User.get()+":"+Password.get()).getBytes()));
	}
}
