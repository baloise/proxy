package com.baloise.proxy;

import static com.baloise.proxy.ImportTLSCert.tool;
import static java.lang.String.format;
import static java.util.Arrays.asList;

import java.awt.TrayIcon.MessageType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.LogManager;

import javax.net.ssl.SSLHandshakeException;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baloise.proxy.config.Config;
import com.baloise.proxy.ui.ProxyUI;
import com.baloise.proxy.ui.ProxyUIAwt;

import common.Password;

public class Proxy {

	private static final Logger log = LoggerFactory.getLogger(Proxy.class);
	private static final String ARG_TEST = "-test";
	private static final String ARG_PWD = "-password=";

	private final ProxyUI ui;
	private Config config;
	private SimpleProxyChain chain;
	private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean();

	public Proxy() {
		this.config = new Config();
		this.ui = new ProxyUIAwt()
				.withMenuEntry("Home",     e -> config.openHome())
				.withMenuEntry("Settings", e -> config.openPropertiesForEditing())
				.withMenuEntry("Password", e -> { if (Password.showDialog()) start(); })
				.withMenuEntry("Test",     e -> test())
				.withMenuEntry("About",    e -> Version.openAbout())
				.withMenuEntry("Restart",  e -> restart())
				.withMenuEntry("Exit",     e -> { log.info("exiting"); System.exit(0); });
		Password.setUI(ui);
		// Dispatch off the FileWatcher thread: a password dialog or chain-start
		// must not block further file events.
		config.onPropertyChange(f -> CompletableFuture.runAsync(this::onConfigChanged));
	}

	private synchronized void onConfigChanged() {
		Config fresh = new Config().reload();
		if (fresh.equals(config)) {
			log.debug("config file changed but effective config identical - ignoring");
			return;
		}
		log.info("config changed - restarting proxy chain");
		config = fresh;
		start();
	}

	private void registerShutdownHook() {
		if (!shutdownHookRegistered.compareAndSet(false, true)) return;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				if (chain != null) chain.stop();
			} catch (Throwable t) {
				log.debug("shutdown hook chain.stop failed", t);
			}
		}, "proxy-shutdown"));
	}

	private void restart(String... args) {
		log.info("restarting JVM");
		if (chain != null) chain.stop();
		try {
			List<String> cmd = new ArrayList<>(asList(
					tool("java"),
					"-cp", System.getProperty("java.class.path"),
					Proxy.class.getName()));
			cmd.addAll(asList(args));
			new ProcessBuilder(cmd).inheritIO().start();
		} catch (IOException e) {
			log.error("Could not restart proxy", e);
			System.exit(667);
		}
		System.exit(0);
	}

	public synchronized void start(String... args) {
		List<String> argList = asList(args);
		argList.stream().filter(a -> a.startsWith(ARG_PWD)).findAny().ifPresent(a -> {
			Password.set(a.substring(ARG_PWD.length()));
			log.info("password set - exiting");
			System.exit(0);
		});

		config.reload();
		if (config.useAuth()) {
			try {
				Password.get();
			} catch (IllegalStateException e) {
				Password.showDialog();
			}
		}

		boolean restarting = chain != null;
		ui.show();
		String msg = restarting ? "restarting" : "starting";
		log.info("proxy {}", msg);
		ui.displayMessage("Proxy", msg);

		if (restarting) chain.stop();
		chain = new SimpleProxyChain(config);
		registerShutdownHook(); // idempotent
		try {
			chain.start(new FiltersSource407(this::onAuthFailure));
		} catch (Exception e) {
			log.error("Proxy startup failed", e);
			ui.showHtml(false, "Start up failure",
					"<b>" + rootMessage(e) + "</b><br/>Exiting.");
			sleepQuietly(5000);
			System.exit(666);
		}

		if (argList.contains(ARG_TEST)) test();
		log.info("proxy started");
	}

	private void onAuthFailure() {
		log.warn("got 407 - asking for new password");
		SwingUtilities.invokeLater(() -> {
			if (Password.showDialog()) {
				if (chain != null) chain.invalidateAuthCache();
				start();
			}
		});
	}

	public boolean test() {
		String url = config.getTestURL();
		InetSocketAddress sa = new InetSocketAddress("127.0.0.1", chain.localPorts[0]);
		log.info("testing {} via {}", url, sa);
		try {
			java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, sa);
			HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection(proxy);
			con.setConnectTimeout(7000);
			int code = con.getResponseCode();
			boolean success = code < 300;
			try (Scanner scan = new Scanner(con.getInputStream())) {
				String text = scan.useDelimiter("\\A").next();
				ui.showHtml(success, url + " - " + code, text);
			}
			return success;
		} catch (SSLHandshakeException e) {
			return handleUntrustedCert(url, sa, e);
		} catch (IOException e) {
			log.warn(e.getMessage(), e);
			ui.displayMessage("Test on '" + url + "' failed", e.getMessage(), MessageType.ERROR);
			return false;
		}
	}

	private boolean handleUntrustedCert(String url, InetSocketAddress sa, SSLHandshakeException cause) {
		try {
			URL u = new URL(url);
			Certificate cert = ImportTLSCert.getCertificate(u.getHost(), portOf(u), sa.getHostString(), sa.getPort());
			X509Certificate x509 = (X509Certificate) cert;
			if (!ui.prompt("Detected untrusted proxy certificate",
					format("Detected untrusted proxy certificate from:\n%s\n\nDo you want to trust the certificate and restart the proxy?",
							x509.getIssuerX500Principal()))) {
				return false;
			}
			File certFile = ImportTLSCert.writeToFile(x509);
			certFile.deleteOnExit();
			ImportTLSCert.importCert(
					ImportTLSCert.getDefaultKeystore(),
					ImportTLSCert.getDefaultPassword(),
					ImportTLSCert.getDefaultAlias(x509),
					certFile.getAbsolutePath());
			restart(ARG_TEST);
			return true;
		} catch (Exception e) {
			log.error("TLS import failed", e);
			return false;
		}
	}

	private static int portOf(URL url) {
		return url.getPort() > 0 ? url.getPort() : url.getDefaultPort();
	}

	private static String rootMessage(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null) c = c.getCause();
		return c.getMessage() == null ? t.getClass().getSimpleName() : c.getMessage();
	}

	private static void sleepQuietly(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		try (InputStream logProps = Proxy.class.getResourceAsStream("logging.properties")) {
			if (logProps != null) LogManager.getLogManager().readConfiguration(logProps);
		}
		new Proxy().start(args);
		// Block main forever - in GUI mode the AWT EDT keeps the JVM alive on its own,
		// but in headless mode all remaining threads may be daemons, so we'd exit otherwise.
		// Use the Exit menu entry (or a SIGTERM/SIGINT) to terminate the process.
		Thread.currentThread().join();
	}
}
