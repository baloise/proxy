package com.baloise.proxy.config;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.System.getenv;
import static java.util.Arrays.asList;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Config {
	
	public static enum UIType {	SWT, AWT;
		public static UIType parse(String uiType) {
			return uiType != null && uiType.toLowerCase().contains("awt") ? AWT : SWT;
		}
	}
	
	private static final String TEST_URL = "testURL";
	private static final String SIMPLE_PROXY_CHAIN_NOPROXY_HOSTS_REG_EX = "SimpleProxyChain.noproxyHostsRegEx";
	private static final String SIMPLE_PROXY_CHAIN_INTERNAL_PORT = "SimpleProxyChain.internalPort";
	private static final String SIMPLE_PROXY_CHAIN_PORT = "SimpleProxyChain.port";
	private static final String SIMPLE_PROXY_CHAIN_UPSTREAM_PORT = "SimpleProxyChain.upstreamPort";
	private static final String SIMPLE_PROXY_CHAIN_UPSTREAM_SERVER = "SimpleProxyChain.upstreamServer";
	private static final String SIMPLE_PROXY_CHAIN_USE_AUTH = "SimpleProxyChain.useAuth";
	private static final String UI = "UI";
	private static final String CHECK_ENVIRONMENT = "checkEnvironment";
	
	public final Path PROXY_HOME = Paths.get(System.getProperty("user.home"), ".proxy");
	public final Path PROXY_PROPERTIES = PROXY_HOME.resolve("proxy.properties");

	Properties defaultProperties = new Properties();
	private Properties lazy_loadedProperties;
	public Config() {
		defaultProperties.setProperty(TEST_URL, "https://example.com/");
		defaultProperties.setProperty(SIMPLE_PROXY_CHAIN_NOPROXY_HOSTS_REG_EX, "--!!!--");
		defaultProperties.setProperty(SIMPLE_PROXY_CHAIN_INTERNAL_PORT, "8889");
		defaultProperties.setProperty(SIMPLE_PROXY_CHAIN_PORT, "8888");
		
		String[] proxyEnv = parseHTTPProxyEnv(detectHTTPProxyEnv().orElse("proxy:8888"));
		
		defaultProperties.setProperty(SIMPLE_PROXY_CHAIN_UPSTREAM_SERVER, proxyEnv[0]);
		defaultProperties.setProperty(SIMPLE_PROXY_CHAIN_UPSTREAM_PORT, proxyEnv[1]);
		defaultProperties.setProperty(SIMPLE_PROXY_CHAIN_USE_AUTH, "false");
		defaultProperties.setProperty(UI, "SWT");
		defaultProperties.setProperty(CHECK_ENVIRONMENT, "true");
		
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

	public static String[] parseHTTPProxyEnv(String proxyEnvString) {
		return proxyEnvString.replaceFirst("(?i)HTTP(S)?://", "").split(":");
	}

	public static Optional<String> detectHTTPProxyEnv() {
		return asList(
				getenv("HTTPS_PROXY"),
				getenv("https_proxy"),
				getenv("HTTP_PROXY"),
				getenv("http_proxy")
			).stream().filter(Objects::nonNull).findFirst();
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
	
	private void setProperty(String key, String value) {
		loadedProperties().setProperty(key, value);
		try (FileOutputStream out = new FileOutputStream(PROXY_PROPERTIES.toFile())) {
			loadedProperties().store(out, null);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean useAuth() {
		return parseBoolean(getProperty(SIMPLE_PROXY_CHAIN_USE_AUTH));
	}
	
	public boolean checkEnvironment() {
		return parseBoolean(getProperty(CHECK_ENVIRONMENT));
	}
	
	public Config setCheckEnvironment(boolean check) {
		setProperty(CHECK_ENVIRONMENT, String.valueOf(check));
		return this;
	}

	public String getUpstreamServer() {
		return getProperty(SIMPLE_PROXY_CHAIN_UPSTREAM_SERVER);
	}
	
	public Config setUpstreamServer(String server) {
		setProperty(SIMPLE_PROXY_CHAIN_UPSTREAM_SERVER, server);
		return this;
	}

	public int getUpstreamPort() {
		return parseInt(getProperty(SIMPLE_PROXY_CHAIN_UPSTREAM_PORT));
	}

	public Config setUpstreamPort(int port) {
		setProperty(SIMPLE_PROXY_CHAIN_UPSTREAM_PORT, String.valueOf(port));
		return this;
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
		return UIType.parse(getProperty(UI));
	}

}
