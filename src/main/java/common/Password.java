package common;

import java.util.Arrays;
import java.util.Map.Entry;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baloise.proxy.ui.ProxyUI;
import com.baloise.proxy.ui.ProxyUI.PasswordDialogResult;

/**
 * User-scoped credential store backed by {@link Preferences}.
 * The password is XOR-obfuscated (not cryptographic) and decryptable only by the same OS user.
 */
public final class Password {

	private static final String PASSWORD = "password";
	private static final Logger log = LoggerFactory.getLogger(Password.class);

	private static volatile ProxyUI ui;

	private Password() {}

	public static void setUI(ProxyUI ui) {
		Password.ui = ui;
	}

	public static boolean showDialog() {
		if (ui == null) throw new IllegalStateException("UI not initialized");
		Entry<PasswordDialogResult, String> result = ui.showPasswordDialog();
		switch (result.getKey()) {
			case REMOVE:
				remove();
				return true;
			case SET:
				String value = result.getValue();
				if (value == null || value.isEmpty()) {
					log.warn("empty password entered - not storing");
					return false;
				}
				set(value);
				return true;
			default:
				return false;
		}
	}

	public static Preferences node() {
		Preferences baloise = Preferences.userRoot().node("com").node("baloise");
		return hasChild(baloise, "windows") ? baloise.node("windows") : baloise.node("proxy").node(PASSWORD);
	}

	public static void set(String pwd) {
		node().put(PASSWORD, Crypto.userEncrypt(pwd));
	}

	public static void remove() {
		node().remove(PASSWORD);
	}

	/**
	 * Retrieve the stored password, prompting the user if missing or corrupt.
	 * Bounded retry loop (up to 3 iterations) to avoid stack-overflow / dialog-spam
	 * when the user repeatedly cancels or enters garbage.
	 */
	public static String get() {
		for (int attempt = 0; attempt < 3; attempt++) {
			String pwd = node().get(PASSWORD, "");
			if (pwd == null || pwd.trim().isEmpty()) {
				if (!showDialog()) break;
				continue;
			}
			try {
				return Crypto.userDecrypt(pwd);
			} catch (IllegalStateException e) {
				log.warn("{} - resetting password.", e.getMessage());
				remove();
			}
		}
		throw new IllegalStateException("You must set the proxy password.");
	}

	private static boolean hasChild(Preferences node, String name) {
		try {
			String[] children = node.childrenNames();
			Arrays.sort(children);
			return Arrays.binarySearch(children, name) > -1;
		} catch (BackingStoreException e) {
			return false;
		}
	}
}
