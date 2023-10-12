package com.baloise.proxy.ui;

import java.awt.Image;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.StringReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baloise.proxy.SVGRasterizer;
import com.sshtools.twoslices.Toast;
import com.sshtools.twoslices.ToastType;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;

public class ProxyUINative implements ProxyUI {

	private transient boolean showing;
	private Image iconImage;
	private final SystemTray tray;
	Logger log = LoggerFactory.getLogger(ProxyUINative.class);

	public ProxyUINative() {
		iconImage = loadSVG("tenancy", "#ffffff");
		tray = SystemTray.get("Proxy");
		tray.setImage(iconImage);
		if (tray == null) {
			throw new RuntimeException("Unable to load SystemTray!");
		}
		tray.setTooltip("proxy");
	}

	BufferedImage loadSVG(String name) {
		return loadSVG(name, "#000000");
	}
	
	BufferedImage loadSVG(String name, String color) {
		try(InputStream inputStream = ProxyUINative.class.getResourceAsStream(name+".svg")) {
			if(inputStream == null) {
				log.warn("svg not found: "+name);				
				return null;
			}
			String xml = new String(inputStream.readAllBytes());
			String style = String.format("<style type=\"text/css\" > <![CDATA[ path { fill: %s; }]]></style>", color);
			xml = xml
					.replace("\"24\"", "\"32\"")
					.replace("</svg>", style +"</svg>");
			return new SVGRasterizer(new StringReader(xml)).createBufferedImage();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public ProxyUI withMenuEntry(String label, ActionListener actionListener) {
		MenuItem entry = new MenuItem(label, actionListener);
		entry.setImage(loadSVG(label.toLowerCase()));
		tray.getMenu().add(entry);
		return this;
	}

	@Override
	public void show() {
		if (showing)
			return;
		showing = true;
	}

	@Override
	public void displayMessage(String caption, String text, MessageType messageType) {
		Toast.toast(mapMessageType(messageType), caption, text);
	}

	protected ToastType mapMessageType(MessageType messageType) {
		switch (messageType) {
		case ERROR:
			return ToastType.ERROR;
		case INFO:
			return ToastType.INFO;
		case NONE:
			return ToastType.NONE;
		case WARNING:
			return ToastType.WARNING;
		}
		throw new IllegalArgumentException("Don't know how to map message type " +messageType);
	}

	@Override
	public Image getIcon() {
		return iconImage;
	}

}
