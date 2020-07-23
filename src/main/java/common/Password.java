package common;

import static javax.swing.JOptionPane.showOptionDialog;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.Icon;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

public class Password {

	private static final String PASSWORD = "password";
	
	private static String appName = null;
	private static Icon appIcon = null;

	private static String lazyPwd;

	public static void main(String[] args) throws BackingStoreException {
		showDialog();
	}

	public static void setDialogBrand(String appName, Icon appIcon) {
		Password.appName = appName;
		Password.appIcon = appIcon;
	}

	public static synchronized boolean showDialog() {
		JPasswordField pass = new JPasswordField(10);
		String title = "Set windows password";
		if (appName != null) {
			title = appName + ": " + title;
		}
		int showOptionDialog = showOptionDialog(
				null, 
				pass, 
				title,
				JOptionPane.NO_OPTION,
				JOptionPane.PLAIN_MESSAGE, 
				appIcon,
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

	private static synchronized void set(String pwd) {
		lazyPwd = Crypto.userEncrypt(pwd);
		node().put(PASSWORD, lazyPwd);
	}

	public static void remove() {
		lazyPwd = null;
		node().remove(PASSWORD);
	}
	
	public static String get() {
		if(isLazyPwdValid()) return lazyPwd;
		lazyPwd = node().get(PASSWORD, "");
		if(!isLazyPwdValid()) {
			if(showDialog()) {
				return get();
			} else {
				throw new IllegalStateException("You must set the windows password.");
			}
		} else {
			return Crypto.userDecrypt(lazyPwd);
		}
	}

	private static boolean isLazyPwdValid() {
		return lazyPwd != null && !lazyPwd.trim().isEmpty();
	}

}
