package com.baloise.proxy.ui;

import static java.util.Arrays.asList;
import static java.util.EnumSet.allOf;

import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.PlainMessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.TitleEvent;
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

public class ProxyUISwt implements ProxyUI, Runnable {

	private Shell shell;
	private TrayItem item;
	private transient Map<String, ActionListener> actions = new HashMap<>();
	private boolean showing;
	private  ImageRegistry images;
	
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
		images = new  ImageRegistry(display);
		allOf(IMAGE.class).forEach((image)->{
			images.put(image.toString(), ImageDescriptor.createFromURL(image.url()));
		});
		Dialog.setDefaultImage(getImage(IMAGE.PROXY_ICON));

		final Tray tray = display.getSystemTray();
		if (tray == null) {
			log.error("The system tray is not available");
		} else {
			item = new TrayItem(tray, SWT.COLOR_TRANSPARENT);
			Menu menu = new Menu(shell, SWT.POP_UP);
			actions.forEach((label, actionListener) -> {
				MenuItem mi = new MenuItem(menu, SWT.PUSH);
				mi.setText(label);
				mi.setImage(getImage(IMAGE.valueOf(label.toUpperCase())));
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
			item.setImage(getImage(IMAGE.PROXY_ICON));
			item.setToolTipText("proxy");
		}
		actions.clear();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}
		display.dispose();
	}

	public Image getImage(final IMAGE image) {
		return images.get(image.toString());
	}

	@Override
	public void displayMessage(String caption, String text, MessageType messageType) {
		Display.getDefault().asyncExec(() -> {
			final ToolTip tip = new ToolTip(shell, SWT.BALLOON | mapMessageType(messageType));
			tip.setMessage(text);
			tip.setText(caption);
			item.setToolTip(tip);
			tip.setVisible(true);
		});
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
			shell.setText(title);
			shell.setImage(getImage(ok ? IMAGE.SUCCESS : IMAGE.FAILURE));
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
		PasswordDialogSwt dialog = new PasswordDialogSwt(shell);
		Display.getDefault().syncExec(()->{
			dialog.open();
		});
		return dialog.result;
	}

	@Override
	public boolean prompt(String caption, String text) {
		PlainMessageDialog dialog = PlainMessageDialog.getBuilder(shell, caption)
				.message(text).buttonLabels(asList("Ok", "Cancel"))
				.build();
		Display.getDefault().syncExec(new Runnable() {
		    public void run() {
		    	dialog.open();
		    }
		});
		return dialog.getReturnCode() == Window.OK;
	}
}
