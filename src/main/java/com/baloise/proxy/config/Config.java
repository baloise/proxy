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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {
	
	public static enum UIType {	SWT, AWT;
		public static UIType parse(String uiType) {
			return uiType != null && uiType.toLowerCase().contains("awt") ? AWT : SWT;
		}
	}
	
	public static enum UpdateMode {	NONE, PROMPT, SILENT;
		public static UpdateMode parse(String mode) {
			try{
				return UpdateMode.valueOf(mode.trim().toUpperCase());
			} catch (Exception e) {
				return PROMPT;
			}
		}
	}
	
	private static final Logger log = LoggerFactory.getLogger(Config.class);
	
	private static final String TEST_URL = "testURL";
	private static final String SIMPLE_PROXY_CHAIN_NOPROXY_HOSTS_REG_EX = "SimpleProxyChain.noproxyHostsRegEx";
	private static final String SIMPLE_PROXY_CHAIN_INTERNAL_PORT = "SimpleProxyChain.internalPort";
	private static final String SIMPLE_PROXY_CHAIN_PORT = "SimpleProxyChain.port";
	private static final String SIMPLE_PROXY_CHAIN_UPSTREAM_PORT = "SimpleProxyChain.upstreamPort";
	private static final String SIMPLE_PROXY_CHAIN_UPSTREAM_SERVER = "SimpleProxyChain.upstreamServer";
	private static final String SIMPLE_PROXY_CHAIN_USE_AUTH = "SimpleProxyChain.useAuth";
	private static final String UI = "UI";
	private static final String CHECK_ENVIRONMENT = "checkEnvironment";
	private static final String CHECK_FOR_UPDATES_FREQUANCY_IN_DAYS = "update.check.freuqency.days";
	private static final String UPDATE_MODE = "update.mode";
	
	public final Path PROXY_HOME = Paths.get(System.getProperty("user.home"), ".proxy");
	public final Path PROXY_PROPERTIES = PROXY_HOME.resolve("proxy.properties");

	private final Properties defaultProperties = new Properties();
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
		defaultProperties.setProperty(UPDATE_MODE, "PROMPT");
		defaultProperties.setProperty(CHECK_ENVIRONMENT, "true");
		defaultProperties.setProperty(CHECK_FOR_UPDATES_FREQUANCY_IN_DAYS, "1");
		
		PROXY_HOME.toFile().mkdirs();
		if (!PROXY_PROPERTIES.toFile().exists()) {
			try (FileOutputStream out = new FileOutputStream(PROXY_PROPERTIES.toFile())) {
				defaultProperties.store(out, null);
				openPropertiesForEditing();
			} catch (IOException e) {
				log.debug(e.getMessage(), e);
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
		open(PROXY_PROPERTIES);
	}

	public void openHome() {
		open(PROXY_HOME);
	}

	private void open(Path path) {
		open(path.toFile(), 1);
	}
	
	private void open(File path, int parents) {
		IOException ex = null;
		while (parents-- > 0) {
			try {
				Desktop.getDesktop().open(path);
			} catch (IOException e) {
				ex = e;
				path = path.getParentFile();
			}
		}
		if(ex != null) {
			log.warn(ex.getMessage(), ex);
		}
	}
	
	
	public Config reload() {
		lazy_loadedProperties = new Properties();
		try (InputStream in = new FileInputStream(PROXY_PROPERTIES.toFile())) {
			lazy_loadedProperties.load(in);
			lazy_loadedProperties.setProperty(UI, getUI().toString());
			lazy_loadedProperties.setProperty(UPDATE_MODE, getUpdateMode().toString());
		} catch (IOException e) {
			log.debug("Could not load properties", e);
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
			log.debug("could not store properties", e);
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
	
	public int checkForUpdatesFrequencyInDays() {
		return parseInt(getProperty(CHECK_FOR_UPDATES_FREQUANCY_IN_DAYS));
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
	
	public UpdateMode getUpdateMode() {
		return UpdateMode.parse(getProperty(UPDATE_MODE));
	}

	@Override
	public int hashCode() {
		return Objects.hash(loadedProperties());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Config other = (Config) obj;
		return Objects.equals(loadedProperties(), other.loadedProperties());
	}

}
