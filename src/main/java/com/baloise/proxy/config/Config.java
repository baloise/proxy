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
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {

	private static final Logger log = LoggerFactory.getLogger(Config.class);

	private static final String TEST_URL = "testURL";
	private static final String NOPROXY_REGEX = "SimpleProxyChain.noproxyHostsRegEx";
	private static final String INTERNAL_PORT = "SimpleProxyChain.internalPort";
	private static final String LOCAL_PORT = "SimpleProxyChain.port";
	private static final String UPSTREAM_PORT = "SimpleProxyChain.upstreamPort";
	private static final String UPSTREAM_SERVER = "SimpleProxyChain.upstreamServer";
	private static final String USE_AUTH = "SimpleProxyChain.useAuth";
	private static final String ALLOW_LOCAL_ONLY = "allowLocalOnly";
	private static final String CONNECT_TIMEOUT_MS = "connectTimeoutMs";
	private static final String IDLE_TIMEOUT_SECONDS = "idleTimeoutSeconds";

	public final Path PROXY_HOME = Paths.get(System.getProperty("user.home"), ".proxy");
	public final Path PROXY_PROPERTIES = PROXY_HOME.resolve("proxy.properties");

	private final Properties defaults = new Properties();
	private Properties loaded;

	public Config() {
		defaults.setProperty(TEST_URL, "https://example.com/");
		defaults.setProperty(NOPROXY_REGEX, "--!!!--");
		defaults.setProperty(INTERNAL_PORT, "8889");
		defaults.setProperty(LOCAL_PORT, "8888");

		String[] proxyEnv = parseHTTPProxyEnv(detectHTTPProxyEnv().orElse("proxy:8888"));
		defaults.setProperty(UPSTREAM_SERVER, proxyEnv[0]);
		defaults.setProperty(UPSTREAM_PORT, proxyEnv[1]);
		defaults.setProperty(USE_AUTH, "false");
		defaults.setProperty(ALLOW_LOCAL_ONLY, "true");
		defaults.setProperty(CONNECT_TIMEOUT_MS, "10000");
		defaults.setProperty(IDLE_TIMEOUT_SECONDS, "70");

		bootstrapPropertiesFile();
	}

	/** Hook for tests to skip file bootstrap. */
	protected void bootstrapPropertiesFile() {
		PROXY_HOME.toFile().mkdirs();
		if (!PROXY_PROPERTIES.toFile().exists()) {
			try (FileOutputStream out = new FileOutputStream(PROXY_PROPERTIES.toFile())) {
				defaults.store(out, null);
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
		try {
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(path.toFile());
			}
		} catch (IOException | UnsupportedOperationException e) {
			log.warn("Could not open {}: {}", path, e.getMessage());
		}
	}

	/**
	 * Reload {@code proxy.properties} from disk. Robust against the file being
	 * temporarily empty / truncated / malformed (common during atomic saves by
	 * editors): in that case the previously-loaded values are kept, so the live
	 * proxy is never silently forced onto defaults mid-edit.
	 */
	public Config reload() {
		File f = PROXY_PROPERTIES.toFile();
		if (!f.exists() || f.length() == 0) {
			log.debug("{} missing or empty - keeping previous config", f);
			if (loaded == null) loaded = new Properties();
			return this;
		}
		Properties next = new Properties();
		try (InputStream in = new FileInputStream(f)) {
			next.load(in);
		} catch (IOException | IllegalArgumentException e) {
			log.warn("Could not parse {}: {} - keeping previous config", f, e.getMessage());
			if (loaded == null) loaded = new Properties();
			return this;
		}
		if (next.isEmpty()) {
			log.debug("parsed {} but it is empty - keeping previous config", f);
			if (loaded == null) loaded = next;
			return this;
		}
		loaded = next;
		return this;
	}

	private Properties loaded() {
		if (loaded == null) reload();
		return loaded;
	}

	private String get(String key) {
		return loaded().getProperty(key, defaults.getProperty(key));
	}

	private void set(String key, String value) {
		loaded().setProperty(key, value);
		try (FileOutputStream out = new FileOutputStream(PROXY_PROPERTIES.toFile())) {
			loaded().store(out, null);
		} catch (IOException e) {
			log.debug("could not store properties", e);
		}
	}

	public boolean useAuth() { return parseBoolean(get(USE_AUTH)); }
	public boolean allowLocalOnly() { return parseBoolean(get(ALLOW_LOCAL_ONLY)); }
	public String getUpstreamServer() { return get(UPSTREAM_SERVER); }
	public int getUpstreamPort() { return parseInt(get(UPSTREAM_PORT)); }
	public int[] getPort() { return parseIntArray(get(LOCAL_PORT)); }
	public int getInternalPort() { return parseInt(get(INTERNAL_PORT)); }
	public String getNoproxyHostsRegEx() { return get(NOPROXY_REGEX); }
	public String getTestURL() { return get(TEST_URL); }
	public int getConnectTimeoutMs() { return parseInt(get(CONNECT_TIMEOUT_MS)); }
	public int getIdleTimeoutSeconds() { return parseInt(get(IDLE_TIMEOUT_SECONDS)); }

	public Config setAllowLocalOnly(boolean allow) { set(ALLOW_LOCAL_ONLY, String.valueOf(allow)); return this; }
	public Config setUpstreamServer(String server) { set(UPSTREAM_SERVER, server); return this; }
	public Config setUpstreamPort(int port) { set(UPSTREAM_PORT, String.valueOf(port)); return this; }

	static int[] parseIntArray(String serialized) {
		if (serialized == null) return new int[0];
		return Arrays.stream(serialized.split("\\D+"))
				.filter(s -> !s.isEmpty())
				.mapToInt(Integer::parseInt)
				.toArray();
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				Arrays.hashCode(getPort()),
				getInternalPort(),
				getUpstreamServer(),
				getUpstreamPort(),
				getNoproxyHostsRegEx(),
				useAuth(),
				allowLocalOnly(),
				getTestURL(),
				getConnectTimeoutMs(),
				getIdleTimeoutSeconds());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Config)) return false;
		Config other = (Config) obj;
		return Arrays.equals(getPort(), other.getPort())
				&& getInternalPort() == other.getInternalPort()
				&& Objects.equals(getUpstreamServer(), other.getUpstreamServer())
				&& getUpstreamPort() == other.getUpstreamPort()
				&& Objects.equals(getNoproxyHostsRegEx(), other.getNoproxyHostsRegEx())
				&& useAuth() == other.useAuth()
				&& allowLocalOnly() == other.allowLocalOnly()
				&& Objects.equals(getTestURL(), other.getTestURL())
				&& getConnectTimeoutMs() == other.getConnectTimeoutMs()
				&& getIdleTimeoutSeconds() == other.getIdleTimeoutSeconds();
	}
}
