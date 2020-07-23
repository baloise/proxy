package com.baloise.proxy;

import java.awt.TrayIcon.MessageType;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Scanner;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baloise.proxy.config.Config;
import com.baloise.proxy.ui.ProxyUI;

import common.Password;

public class Proxy {
	
	private ProxyUI ui;
	private SimpleProxyChain simpleProxyChain;
	private Config config;
	Logger log = LoggerFactory.getLogger(Proxy.class);

	public Proxy() {
		config = new Config();
		Password.setDialogBrand("Proxy", new ImageIcon(ProxyUI.createIcon()));
		ui = new ProxyUI()
		.withMenuEntry("Config", e -> {
			config.openProperties();
		})
		.withMenuEntry("Password", e -> {
			Password.showDialog();
			ui.displayMessage("Proxy","new password set", MessageType.INFO);
		})
		.withMenuEntry("Test", e -> {
			test(config.load().getProperty("testURL", "http://example.com/"));
		});
		config.onPropertyChange(f -> start());
	}

	public void start() {
		log.info("using slf4j SimpleLogger, for configuration see https://www.slf4j.org/api/org/slf4j/impl/SimpleLogger.html");
		try {
			Password.get();			
		} catch (IllegalStateException e) {
			Password.showDialog();
		}
		ui.show();
		ui.displayMessage("Proxy",simpleProxyChain == null ? "starting ..." : "restarting ...", MessageType.INFO);
		if(simpleProxyChain != null) simpleProxyChain.stop();
		simpleProxyChain = new SimpleProxyChain(config.load());
		log.info("Proxy starting");
		simpleProxyChain.start(new FiltersSource407(() -> {
			log.warn("got 407 - asking for new password");
			SwingUtilities.invokeLater(()->{
				Password.showDialog();
				start();				
			});
			simpleProxyChain.stop();
		}));
	}
	
	public static void main(String[] args) {
		new Proxy().start();
	}

	public boolean test(String url) {
		InetSocketAddress sa = new InetSocketAddress("127.0.0.1", simpleProxyChain.LOCAL_PORTS[0]);
		log.info("testing "+sa);
		java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, sa);
		try {
			HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection(proxy);
			try (Scanner scan = new Scanner(con.getInputStream())) {
				String text = scan.useDelimiter("\\A").next();
				log.info(text);
				JOptionPane.showMessageDialog(null, text , url+" - "+con.getResponseCode(),  con.getResponseCode() < 300 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
			}
			return con.getResponseCode() < 300;
		} catch (IOException e) {
			log.warn(e.getMessage(), e);
			JOptionPane.showMessageDialog(null, e.getMessage() , url, JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

}
