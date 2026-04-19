package com.baloise.proxy.ui;

import static javax.swing.JOptionPane.showOptionDialog;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionListener;
import java.util.AbstractMap;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AWT/Swing UI: system tray with popup menu. Degrades gracefully to logger-only
 * when headless or when the tray is unsupported (CI, servers, containers).
 */
public class ProxyUIAwt implements ProxyUI {

	private static final Logger log = LoggerFactory.getLogger(ProxyUIAwt.class);

	private final boolean headless = GraphicsEnvironment.isHeadless() || !SystemTray.isSupported();
	// PopupMenu touches AWT native code and throws HeadlessException at construction
	// time on headless JVMs, so build it lazily only when we actually have a display.
	private java.awt.PopupMenu popupMenu;
	private TrayIcon trayIcon;
	private ImageIcon icon;
	private boolean shown;

	private java.awt.PopupMenu popupMenu() {
		if (popupMenu == null) popupMenu = new java.awt.PopupMenu();
		return popupMenu;
	}

	@Override
	public synchronized void show() {
		if (shown) return;
		shown = true;
		if (headless) {
			log.info("UI headless - proxy running without tray icon");
			return;
		}
		icon = new ImageIcon(IMAGE.PROXY_ICON.url());
		trayIcon = new TrayIcon(icon.getImage(), "proxy", popupMenu());
		trayIcon.setImageAutoSize(true);
		try {
			SystemTray.getSystemTray().add(trayIcon);
		} catch (AWTException e) {
			log.error("TrayIcon could not be added", e);
		}
	}

	@Override
	public ProxyUI withMenuEntry(String label, ActionListener actionListener) {
		if (headless) return this;
		java.awt.MenuItem item = new java.awt.MenuItem(label);
		item.addActionListener(actionListener);
		popupMenu().add(item);
		return this;
	}

	@Override
	public void displayMessage(String caption, String text, MessageType messageType) {
		if (trayIcon != null) {
			trayIcon.displayMessage(caption, text, messageType);
		} else {
			log.info("{}: {} - {}", messageType, caption, text);
		}
	}

	@Override
	public void showHtml(boolean success, String title, String html) {
		if (headless) {
			log.info("{} - {}", title, html);
			return;
		}
		JOptionPane.showMessageDialog(null, html, title,
				success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
	}

	@Override
	public boolean prompt(String caption, String text) {
		if (headless) {
			log.warn("prompt ignored in headless: {} - {}", caption, text);
			return false;
		}
		return JOptionPane.showConfirmDialog(null, text, caption, JOptionPane.OK_CANCEL_OPTION) == 0;
	}

	@Override
	public Map.Entry<PasswordDialogResult, String> showPasswordDialog() {
		if (headless) {
			log.warn("password dialog not available in headless mode");
			return new AbstractMap.SimpleEntry<>(PasswordDialogResult.CANCEL, "");
		}
		JPasswordField pass = new JPasswordField(10);
		int option = showOptionDialog(
				null,
				pass,
				"Set proxy password",
				JOptionPane.NO_OPTION,
				JOptionPane.PLAIN_MESSAGE,
				icon,
				new String[]{"Set password", "Remove password", "Cancel"},
				"Set password");
		return new AbstractMap.SimpleEntry<>(PasswordDialogResult.ofOption(option), new String(pass.getPassword()));
	}
}
