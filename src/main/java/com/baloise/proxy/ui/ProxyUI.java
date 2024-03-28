package com.baloise.proxy.ui;

import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ProxyUI {
	Logger log = LoggerFactory.getLogger(ProxyUIAwt.class);
	
	public static enum PasswordDialogResult {
		SET, REMOVE, CANCEL;

		static PasswordDialogResult ofValue(int option) {
			switch (option) {
				case 0: 	return SET;
				case 1: 	return REMOVE;
				default:	return CANCEL;
			}
		}
	}

	public static enum IMAGE {
		ABOUT, EXIT, FAILURE, PASSWORD, PROXY_ICON, RESTART, SETTINGS, SUCCESS, TEST;

		public URL url() {
			return ProxyUI.class.getResource(toString().toLowerCase() + ".png");
		}
	}

	ProxyUI withMenuEntry(String label, ActionListener actionListener);

	void show();

	
	default void displayMessage(String caption, String text) {
		displayMessage(caption, text, MessageType.INFO);
	}
	
	void displayMessage(String caption, String text, MessageType messageType);

	void showHTLM(boolean success, String title, String html);
	
	boolean prompt(String caption, String text);

	Entry<PasswordDialogResult, String> showPasswordDialog();

}