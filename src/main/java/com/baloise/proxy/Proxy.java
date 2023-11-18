package com.baloise.proxy;

import java.awt.TrayIcon.MessageType;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Scanner;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baloise.proxy.config.Config;
import com.baloise.proxy.ui.ProxyUI;
import com.baloise.proxy.ui.ProxyUIAwt;
import com.baloise.proxy.ui.ProxyUISwt;

import common.Password;

public class Proxy {
	
	private ProxyUI ui;
	private SimpleProxyChain simpleProxyChain;
	private Config config;
	Logger log = LoggerFactory.getLogger(Proxy.class);


	public Proxy() {
		config = new Config();
		ui = createUI()
		.withMenuEntry("Settings", e -> {
			config.openPropertiesForEditing();
		})
		.withMenuEntry("Password", e -> {
			if(Password.showDialog()) {
				start();
			}
		})
		.withMenuEntry("Test", e -> {
			test(config.getTestURL());
		}).withMenuEntry("Exit", e -> {
			log.info("Exiting...");
			System.exit(0);
		});
		Password.ui = ui;
		config.onPropertyChange(f -> start());
	}

	ProxyUI createUI() {
		switch (config.getUI()) {
			case AWT: return new ProxyUIAwt();
			default: return new ProxyUISwt();
		}
	} 
	
	public void start() {
		log.info("using slf4j SimpleLogger, for configuration see https://www.slf4j.org/api/org/slf4j/impl/SimpleLogger.html");
		config.reload();
		try {
			if(config.useAuth()) Password.get();			
		} catch (IllegalStateException e) {
			Password.showDialog();
		}
		ui.show();				
		ui.displayMessage("Proxy",simpleProxyChain == null ? "starting ..." : "restarting ...", MessageType.INFO);
		if(simpleProxyChain != null) simpleProxyChain.stop();
		simpleProxyChain = new SimpleProxyChain(config);
		log.info("Proxy starting");
		try {
			simpleProxyChain.start(new FiltersSource407(() -> {
				log.warn("got 407 - asking for new password");
				SwingUtilities.invokeLater(()->{
					Password.showDialog();
					start();				
				});
				simpleProxyChain.stop();
			}));
		} catch (Exception e) {
			e.printStackTrace();
			ui.displayMessage("Start up failure", e.getCause().getMessage() +"\nExiting.", MessageType.ERROR);
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e1) {
			}
			System.exit(666);
		}
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
			final boolean success = con.getResponseCode() < 300;
			try (Scanner scan = new Scanner(con.getInputStream())) {
				String text = scan.useDelimiter("\\A").next();
				log.debug(text);
				ui.showHTLM(success, url+" - "+con.getResponseCode(), text);
			}
			return success;
		} catch (IOException e) {
			log.warn(e.getMessage(), e);
			ui.displayMessage("Test on '"+url+"' failed", e.getMessage(), MessageType.ERROR);
			return false;
		}
	}
	
	
}
