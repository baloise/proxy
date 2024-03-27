package com.baloise.proxy.ui;

import static javax.swing.JOptionPane.showOptionDialog;

import java.awt.AWTException;
import java.awt.Frame;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.AbstractMap;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

public class ProxyUIAwt implements ProxyUI {
	private SystemTray tray;
	private PopupMenu popupMenu;
	private transient boolean showing;
	private ImageIcon icon;

	public ProxyUIAwt() {
		try {
			tray = SystemTray.getSystemTray();
		} catch (UnsupportedOperationException e) {
			System.err.println(e.getMessage());
		}
		popupMenu = new PopupMenu();
	}

	@Override
	public void displayMessage(String caption, String text, MessageType messageType) {
		if (tray == null) {
			JOptionPane.showMessageDialog(null, text, caption, mapMessageType(messageType));
		} else {
			tray.getTrayIcons()[0].displayMessage(caption, text, messageType);
		}
	}

	private int mapMessageType(MessageType messageType) {
		switch (messageType) {
		case ERROR:
			return JOptionPane.ERROR_MESSAGE;
		case INFO:
			return JOptionPane.INFORMATION_MESSAGE;
		case NONE:
			return JOptionPane.PLAIN_MESSAGE;
		case WARNING:
			return JOptionPane.WARNING_MESSAGE;
		default:
			throw new IllegalArgumentException("Don't know how to map " + messageType);
		}
	}

	@Override
	public void show() {
		if (showing)
			return;
		showing = true;

		icon = new ImageIcon(IMAGE.PROXY_ICON.url());
		if (tray == null) {
			final Frame f = new Frame("Tray Workaround");
			f.addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent we) {
					f.dispose();
				}
			});
			f.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent e) {
					popupMenu.show(f, e.getX(), e.getY());
				}
			});
			f.add(popupMenu);
			f.setSize(400, 400);
			f.setLayout(null);
			f.setVisible(true);
		} else {
			TrayIcon trayIcon = new TrayIcon(icon.getImage(), "proxy", popupMenu) {
			};
			trayIcon.setImageAutoSize(true);
			try {
				tray.add(trayIcon);
			} catch (AWTException e) {
				log.error("TrayIcon could not be added.", e);
			}
		}
	}

	@Override
	public ProxyUI withMenuEntry(String label, ActionListener actionListener) {
		MenuItem item = new MenuItem(label);
		item.addActionListener(actionListener);
		popupMenu.add(item);
		return this;
	}

	@Override
	public void showHTLM(boolean success, String title, String html) {
		JOptionPane.showMessageDialog(null, html , title,  success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
	}

	@Override
	public Map.Entry<PasswordDialogResult, String> showPasswordDialog() {
		final JPasswordField pass = new JPasswordField(10);
		int option = showOptionDialog(
				null, 
				pass, 
				"Set proxy password",
				JOptionPane.NO_OPTION,
				JOptionPane.PLAIN_MESSAGE, 
				icon,
				new String[] { "Set password","Remove password", "Cancel" }, "Set password");
		return new AbstractMap.SimpleEntry<>(PasswordDialogResult.ofValue(option), new String(pass.getPassword()));
	}

	@Override
	public boolean prompt(String caption, String text) {
		return JOptionPane.showConfirmDialog(null, text, caption, JOptionPane.OK_CANCEL_OPTION) ==0;
	}
	
}
