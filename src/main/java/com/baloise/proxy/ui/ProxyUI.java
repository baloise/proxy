package com.baloise.proxy.ui;

import java.awt.Image;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionListener;

public interface ProxyUI {

	ProxyUI withMenuEntry(String label, ActionListener actionListener);

	void show();

	void displayMessage(String caption, String text, MessageType messageType);

	Image getIcon();

}