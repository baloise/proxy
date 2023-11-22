package com.baloise.proxy.ui;

import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map.Entry;
import java.util.function.Function;

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
		SUCCESS, FAILURE, PROXY_ICON;

		public String png() {
			return toString().toLowerCase() + ".png";
		}
		
		public URL url() {
			return ProxyUI.class.getResource(png());
		}
	}

	ProxyUI withMenuEntry(String label, ActionListener actionListener);

	void show();

	void displayMessage(String caption, String text, MessageType messageType);

	void showHTLM(boolean success, String title, String html);

	Entry<PasswordDialogResult, String> showPasswordDialog();

}