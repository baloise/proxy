package com.baloise.proxy.ui;

import static java.util.Arrays.asList;
import static java.util.EnumSet.allOf;

import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyUISwt implements ProxyUI, Runnable {

	
	private Shell shell;
	private TrayItem item;
	private transient List<Map.Entry<String, ActionListener>> actions = new ArrayList<>();
	private boolean showing;
	private  ImageRegistry images;
	private Display display;
	private static final Logger log = LoggerFactory.getLogger(ProxyUISwt.class);
	
	@Override
	public ProxyUI withMenuEntry(String label, ActionListener actionListener) {
		actions.add(new AbstractMap.SimpleEntry<>(label, actionListener));
		return this;
	}

	@Override
	public void show() {
		if (showing)
			return;
		showing = true;
		new Thread(this).start();
		while(shell == null) {
			log.debug("waiting for shell creation...");
			try {
				Thread.sleep(79);
			} catch (InterruptedException expected) {}
		}
	}

	@Override
	public void run() {
		display = new Display();
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
			actions.forEach(e -> {
				String label = e.getKey();
				ActionListener actionListener = e.getValue();
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
		tray.dispose();
		display.dispose();
	}

	public Image getImage(final IMAGE image) {
		return images.get(image.toString());
	}

	@Override
	public void displayMessage(String caption, String text, MessageType messageType) {
		display.asyncExec(() -> {
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
		display.asyncExec(()->{
			Shell shell = new Shell(display);
			shell.setLayout(new FillLayout());
			shell.setText(title);
			shell.setImage(getImage(ok ? IMAGE.SUCCESS : IMAGE.FAILURE));
			Browser browser = new Browser(shell, SWT.NONE);
			browser.addTitleListener((TitleEvent event) -> {
				shell.setText(title+ " | "+event.title);
			});
			browser.setBounds(0,0,600,400);
			browser.setText(html);
			shell.pack();
			shell.open();
		});
	}

	
	@Override
	public Entry<PasswordDialogResult, String> showPasswordDialog() {
		PasswordDialogSwt dialog = new PasswordDialogSwt(shell);
		display.syncExec(()->{
			dialog.open();
		});
		return dialog.result;
	}

	@Override
	public boolean prompt(String caption, String text) {
		PlainMessageDialog dialog = PlainMessageDialog.getBuilder(shell, caption)
				.message(text).buttonLabels(asList("Ok", "Cancel"))
				.build();
		display.syncExec(() -> {
		    	dialog.open();
		});
		return dialog.getReturnCode() == Window.OK;
	}
}
