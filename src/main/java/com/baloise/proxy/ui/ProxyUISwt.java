package com.baloise.proxy.ui;

import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.TitleEvent;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.Tray;
import org.eclipse.swt.widgets.TrayItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyUISwt implements ProxyUI, Runnable {

	private Shell shell;
	private TrayItem item;
	private transient Map<String, ActionListener> actions = new HashMap<>();
	private boolean showing;
	private Image icon, success, failure;
	Logger log = LoggerFactory.getLogger(ProxyUISwt.class);
	
	@Override
	public ProxyUI withMenuEntry(String label, ActionListener actionListener) {
		actions.put(label, actionListener);
		return this;
	}

	@Override
	public void show() {
		if (showing)
			return;
		showing = true;
		new Thread(this).start();
	}

	@Override
	public void run() {
		Display display = new Display();
		shell = new Shell(display);

		icon = loadImage(IMAGE.PROXY_ICON, (in) -> new Image(display, in));
		Dialog.setDefaultImage(icon);
		success = loadImage(IMAGE.SUCCESS, (in) -> new Image(display, in));
		failure = loadImage(IMAGE.FAILURE, (in) -> new Image(display, in));

		final Tray tray = display.getSystemTray();
		if (tray == null) {
			log.error("The system tray is not available");
		} else {
			item = new TrayItem(tray, SWT.COLOR_TRANSPARENT);
			Menu menu = new Menu(shell, SWT.POP_UP);
			actions.forEach((label, actionListener) -> {
				MenuItem mi = new MenuItem(menu, SWT.PUSH);
				mi.setText(label);
				mi.addListener(SWT.Selection, new Listener() {
					public void handleEvent(Event event) {
						new Thread(() -> actionListener.actionPerformed(new ActionEvent(mi, ActionEvent.ACTION_PERFORMED, label))).start();
					}
				});
			});
			item.addListener(SWT.MenuDetect, new Listener() {
				public void handleEvent(Event event) {
					menu.setVisible(true);
				}
			});
			item.setImage(icon);
			item.setToolTipText("proxy");
		}
		actions.clear();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		icon.dispose();
		success.dispose();
		failure.dispose();
		display.dispose();
	}

	@Override
	public void displayMessage(String caption, String text, MessageType messageType) {
		int retry = 17;
		while (shell == null && retry-- > 0) sleep79();
		Display.getDefault().asyncExec(() -> {
			final ToolTip tip = new ToolTip(shell, SWT.BALLOON | mapMessageType(messageType));
			tip.setMessage(text);
			tip.setText(caption);
			item.setToolTip(tip);
			tip.setVisible(true);
		});
	}

	private void sleep79() {
		try { Thread.sleep(79); } catch (InterruptedException e) {}
	}

	protected int mapMessageType(MessageType messageType) {
		switch (messageType) {
		case ERROR:
			return SWT.ICON_ERROR;
		case INFO:
			return SWT.ICON_INFORMATION;
		case NONE:
			return SWT.ICON_WORKING;
		case WARNING:
			return SWT.ICON_WARNING;
		}
		throw new IllegalArgumentException("Don't know how to map message type " + messageType);
	}

	@Override
	public void showHTLM(boolean ok, String title, String html) {
		new Thread(() -> {
			Display display = new Display();
			final Shell shell = new Shell(display, SWT.SHELL_TRIM);
			shell.setLayout(new FillLayout());
			shell.setText(success + " | "+title);
			shell.setImage(ok ? success : failure);
			Browser browser = new Browser(shell, SWT.NONE);
			browser.addTitleListener((TitleEvent event) -> {
				shell.setText(title+ " | "+event.title);
			});
			browser.setBounds(0,0,600,400);
			shell.pack();
			shell.open();
			browser.setText(html);
			while (!shell.isDisposed())
				if (!display.readAndDispatch())
					display.sleep();
		}).start();
	}

	
	@Override
	public Entry<PasswordDialogResult, String> showPasswordDialog() {
		List<PasswordDialogSwt> diaL = new ArrayList<>(1);
		Display.getDefault().syncExec(()->{
			PasswordDialogSwt dialog = new PasswordDialogSwt(shell);
			diaL.add(dialog);
			dialog.open();
		});
		while(diaL.isEmpty() || diaL.get(0).result == null) sleep79();
		return diaL.get(0).result;
	}

}
