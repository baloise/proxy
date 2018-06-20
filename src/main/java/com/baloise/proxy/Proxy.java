package com.baloise.proxy;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Scanner;

import javax.swing.SwingUtilities;

import com.baloise.proxy.config.Config;
import com.baloise.proxy.ui.ProxyUI;

import common.Password;

public class Proxy {
	
	private ProxyUI ui;
	private SimpleProxyChain simpleProxyChain;
	private Config config;

	public Proxy() {
		config = new Config();
		ui = new ProxyUI()
		.withMenuEntry("Config", e -> {
			config.openProperties();
		})
		.withMenuEntry("Password", e -> {
			Password.showDialog();
		})
		.withMenuEntry("Test", e -> {
			test(config.load().getProperty("testURL", "http://example.com/"));
		});
		config.onPropertyChange(f -> start());
	}

	public void start() {
		System.out.println("starting");
		Password.get();
		if(simpleProxyChain != null) simpleProxyChain.stop();
		simpleProxyChain = new SimpleProxyChain(config.load());
		simpleProxyChain.start(new FiltersSource407(() -> {
			SwingUtilities.invokeLater(()->{
				Password.showDialog();
				start();				
			});
			simpleProxyChain.stop();
		}));
		ui.show();
	}
	
	public static void main(String[] args) {
		new Proxy().start();
	}

	public boolean test(String url) {
		java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", simpleProxyChain.PORT));
		try {
			HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection(proxy);
			try (Scanner scan = new Scanner(con.getInputStream())) {
				String text = scan.useDelimiter("\\A").next();
				System.out.println(text);
			}
			return con.getResponseCode() < 300;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

}
