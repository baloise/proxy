package com.baloise.proxy.ui;

import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.Map.Entry;

public interface ProxyUI {

	enum PasswordDialogResult {
		SET, REMOVE, CANCEL;

		public static PasswordDialogResult ofOption(int option) {
			switch (option) {
				case 0:  return SET;
				case 1:  return REMOVE;
				default: return CANCEL;
			}
		}
	}

	enum IMAGE {
		ABOUT, EXIT, FAILURE, HOME, PASSWORD, PROXY_ICON, RESTART, SETTINGS, SUCCESS, TEST;

		public URL url() {
			return ProxyUI.class.getResource(name().toLowerCase() + ".png");
		}
	}

	ProxyUI withMenuEntry(String label, ActionListener actionListener);

	void show();

	default void displayMessage(String caption, String text) {
		displayMessage(caption, text, MessageType.INFO);
	}

	void displayMessage(String caption, String text, MessageType messageType);

	void showHtml(boolean success, String title, String html);

	boolean prompt(String caption, String text);

	Entry<PasswordDialogResult, String> showPasswordDialog();
}
