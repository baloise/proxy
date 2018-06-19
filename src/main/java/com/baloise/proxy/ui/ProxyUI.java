package com.baloise.proxy.ui;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

public class ProxyUI {
	private SystemTray tray;
	private PopupMenu popupMenu;
	private boolean showing;
	
	public ProxyUI() {
		tray = SystemTray.getSystemTray();
		createIcon();
	    
		popupMenu = new PopupMenu();
	}
	
	public BufferedImage createIcon() {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();

	    g2d.setColor(Color.red);
	    g2d.fill(new Ellipse2D.Float(0, 0, 50, 30));
	    g2d.setColor(Color.green);
	    g2d.fill(new Ellipse2D.Float(0, 30, 50, 40));
	    g2d.dispose();
	    return image;
	}
	
	public void show() {
		if(showing) return;
		showing = true;
		
		withMenuEntry("Exit", e -> {
			System.out.println("Exiting...");
			System.exit(0);
		});
		
		TrayIcon trayIcon = new TrayIcon(createIcon(), "proxy", popupMenu);
		trayIcon.setImageAutoSize(true);
		
		try {
			tray.add(trayIcon);
		} catch (AWTException e) {
			System.err.println("TrayIcon could not be added.");
		}
		
	}
	
	public ProxyUI withMenuEntry(String label, ActionListener actionListener) {
		MenuItem item = new MenuItem(label);
		item.addActionListener(actionListener);
		popupMenu.add(item);
		return this;
	}
}
