package common;

import static javax.swing.JOptionPane.showOptionDialog;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

public class Password {

	private static final String PASSWORD = "password";
	
	public static void main(String[] args) throws BackingStoreException {
		showDialog();
	}

	public static boolean showDialog() {
		JPasswordField pass = new JPasswordField(10);
		int showOptionDialog = showOptionDialog(
				null, 
				pass, 
				"Set windows password", 
				JOptionPane.NO_OPTION,
				JOptionPane.PLAIN_MESSAGE, 
				null, 
				new String[] { "Set password","Remove password", "Cancel" }, "Set password");
		if (showOptionDialog == 0) {
			set(new String(pass.getPassword()));
			return true;
		} 
		if (showOptionDialog == 1) {
			 remove();
		} 
		return false;
	}
	
	public static Preferences node() {
		return Preferences.userRoot().node("com").node("baloise").node("windows");
	}

	private static void set(String pwd) {
		node().put(PASSWORD, Crypto.userEncrypt(pwd));
	}

	public static void remove() {
		node().remove(PASSWORD);
	}
	
	public static String get() {
		String pwd = node().get(PASSWORD, "");
		if(pwd == null || pwd.trim().isEmpty()) {
			if(showDialog()) {
				return get();
			} else {
				throw new IllegalStateException("You must set the windows password.");
			}
		} else {
			return Crypto.userDecrypt(pwd);
		}
	}

}
