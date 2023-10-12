package com.baloise.proxy;

import static java.lang.Boolean.parseBoolean;

import java.awt.TrayIcon.MessageType;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Properties;
import java.util.Scanner;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baloise.proxy.config.Config;
import com.baloise.proxy.ui.ProxyUI;
import com.baloise.proxy.ui.ProxyUIAwt;
import com.baloise.proxy.ui.ProxyUINative;

import common.Password;

public class Proxy {
	
	private ProxyUI ui;
	private SimpleProxyChain simpleProxyChain;
	private Config config;
	Logger log = LoggerFactory.getLogger(Proxy.class);

	public Proxy() {
		config = new Config();
		ui = (System.getProperty("proxy.ui", "native").equalsIgnoreCase("awt") ? new ProxyUIAwt() : new ProxyUINative())
		.withMenuEntry("Settings", e -> {
			config.openProperties();
		})
		.withMenuEntry("Password", e -> {
			Password.showDialog();
			start();
		})
		.withMenuEntry("Test", e -> {
			test(config.load().getProperty("testURL", "http://example.com/"));
		}).withMenuEntry("Exit", e -> {
			log.info("Exiting...");
			System.exit(0);
		});
		Password.setDialogBrand("Proxy", new ImageIcon(ui.getIcon()));
		config.onPropertyChange(f -> start());
	}

	public void start() {
		Properties props = config.load();
		try {
			if(parseBoolean(props.getProperty("SimpleProxyChain.useAuth", "false"))) Password.get();			
		} catch (IllegalStateException e) {
			Password.showDialog();
		}
		ui.show();
		ui.displayMessage("Proxy",simpleProxyChain == null ? "starting ..." : "restarting ...", MessageType.INFO);
		if(simpleProxyChain != null) simpleProxyChain.stop();
		simpleProxyChain = new SimpleProxyChain(props);
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
			System.exit(0);System.exit(666);
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
