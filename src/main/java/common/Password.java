package common;

import java.util.Arrays;
import java.util.Map.Entry;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baloise.proxy.ui.ProxyUI;
import com.baloise.proxy.ui.ProxyUI.PasswordDialogResult;

public class Password {

	private static final String PASSWORD = "password";
	public static ProxyUI ui;

	static Logger log = LoggerFactory.getLogger(Password.class);

	public static boolean hasChild(final Preferences node, final String name) {
		try {
			String[] childrenNames = node.childrenNames();
			Arrays.sort(childrenNames);
			return Arrays.binarySearch(childrenNames, name) > -1;
		} catch (BackingStoreException e) {
			return false;
		}
	}

	public static void main(String[] args) throws BackingStoreException {
		showDialog();
	}

	public static boolean showDialog() {
		Entry<PasswordDialogResult, String> result = ui.showPasswordDialog();
		switch (result.getKey()) {
		default:
			return false;
		case REMOVE:
			remove();
			return true;
		case SET:
			set(result.getValue());
			return true;
		}
	}

	public static Preferences node() {
		final Preferences baloise = Preferences.userRoot().node("com").node("baloise");
		return hasChild(baloise, "windows") ? baloise.node("windows") : baloise.node("proxy").node(PASSWORD);
	}

	private static void set(String pwd) {
		node().put(PASSWORD, Crypto.userEncrypt(pwd));
	}

	public static void remove() {
		node().remove(PASSWORD);
	}

	public static String get() {
		String pwd = node().get(PASSWORD, "");
		if (pwd == null || pwd.trim().isEmpty()) {
			if (showDialog()) {
				return get();
			} else {
				throw new IllegalStateException("You must set the proxy password.");
			}
		} else {
			try {
				return Crypto.userDecrypt(pwd);
			} catch (IllegalStateException e) {
				log.warn(e.getMessage() + " - resetting password.");
				remove();
				return get();
			}
		}
	}

}
