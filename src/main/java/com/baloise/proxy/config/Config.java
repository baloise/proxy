package com.baloise.proxy.config;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Config {
	
	public static enum UIType {	SWT, AWT;}
	
	private static final String TEST_URL = "testURL";
	private static final String SIMPLE_PROXY_CHAIN_NOPROXY_HOSTS_REG_EX = "SimpleProxyChain.noproxyHostsRegEx";
	private static final String SIMPLE_PROXY_CHAIN_INTERNAL_PORT = "SimpleProxyChain.internalPort";
	private static final String SIMPLE_PROXY_CHAIN_PORT = "SimpleProxyChain.port";
	private static final String SIMPLE_PROXY_CHAIN_UPSTREAM_PORT = "SimpleProxyChain.upstreamPort";
	private static final String SIMPLE_PROXY_CHAIN_UPSTREAM_SERVER = "SimpleProxyChain.upstreamServer";
	private static final String SIMPLE_PROXY_CHAIN_USE_AUTH = "SimpleProxyChain.useAuth";
	private static final String UI = "UI";
	
	public final Path PROXY_HOME = Paths.get(System.getProperty("user.home"), ".proxy");
	public final Path PROXY_PROPERTIES = PROXY_HOME.resolve("proxy.properties");

	Properties defaultProperties = new Properties();
	private Properties lazy_loadedProperties;
	public Config() {
		defaultProperties.setProperty(TEST_URL, "https://example.com/");
		defaultProperties.setProperty(SIMPLE_PROXY_CHAIN_NOPROXY_HOSTS_REG_EX, "--!!!--");
		defaultProperties.setProperty(SIMPLE_PROXY_CHAIN_INTERNAL_PORT, "8889");
		defaultProperties.setProperty(SIMPLE_PROXY_CHAIN_PORT, "8888");
		defaultProperties.setProperty(SIMPLE_PROXY_CHAIN_UPSTREAM_PORT, "8888");
		defaultProperties.setProperty(SIMPLE_PROXY_CHAIN_UPSTREAM_SERVER, "proxy");
		defaultProperties.setProperty(SIMPLE_PROXY_CHAIN_USE_AUTH, "false");
		defaultProperties.setProperty(UI, "SWT");
		
		PROXY_HOME.toFile().mkdirs();
		if (!PROXY_PROPERTIES.toFile().exists()) {
			try (FileOutputStream out = new FileOutputStream(PROXY_PROPERTIES.toFile())) {
				defaultProperties.store(out, null);
				openPropertiesForEditing();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void onPropertyChange(Consumer<File> onChange) {
		new FileWatcher(PROXY_PROPERTIES.toFile(), onChange).start();
	}

	public void openPropertiesForEditing() {
		File file = PROXY_PROPERTIES.toFile();
		try {
			Desktop.getDesktop().open(file);
		} catch (IOException e) {
			try {
				Desktop.getDesktop().open(file.getParentFile());
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}
	
	public Config reload() {
		lazy_loadedProperties = new Properties();
		try (InputStream in = new FileInputStream(PROXY_PROPERTIES.toFile())) {
			lazy_loadedProperties.load(in);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return this;
	}
	
	private Properties loadedProperties() {
		if(lazy_loadedProperties == null) reload();
		return lazy_loadedProperties;
	}

	private String getProperty(String key) {
		return loadedProperties().getProperty(key, defaultProperties.getProperty(key));
	}

	public boolean useAuth() {
		return parseBoolean(getProperty(SIMPLE_PROXY_CHAIN_USE_AUTH));
	}

	public String getUpstreamServer() {
		return getProperty(SIMPLE_PROXY_CHAIN_UPSTREAM_SERVER);
	}

	public int getUpstreamPort() {
		return parseInt(getProperty(SIMPLE_PROXY_CHAIN_UPSTREAM_PORT));
	}

	public int[] getPort() {
		return parseIntArray(getProperty(SIMPLE_PROXY_CHAIN_PORT));
	}

	static int[] parseIntArray(String serializedIntArray) {
		return Stream.of(serializedIntArray.split("\\D+")).mapToInt(Integer::parseInt).toArray();
	}

	public int getInternalPort() {
		return parseInt(getProperty(SIMPLE_PROXY_CHAIN_INTERNAL_PORT));
	}

	public String getNoproxyHostsRegEx() {
		return getProperty(SIMPLE_PROXY_CHAIN_NOPROXY_HOSTS_REG_EX);
	}

	public String getTestURL() {
		return getProperty(TEST_URL);
	}
	
	public UIType getUI() {
		return UIType.valueOf(getProperty(UI));
	}

}
