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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import javax.net.ssl.SSLHandshakeException;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baloise.proxy.config.Config;
import com.baloise.proxy.ui.ProxyUI;
import com.baloise.proxy.ui.ProxyUIAwt;
import com.baloise.proxy.ui.ProxyUISwt;

import common.OperatingSystem;
import common.Password;

public class Proxy {
	
	private static final String ARG_TEST = "test";
	private ProxyUI ui;
	private SimpleProxyChain simpleProxyChain;
	private Config config;
	Logger log = LoggerFactory.getLogger(Proxy.class);


	public Proxy() {
		config = new Config();
		ui = createUI()
		.withMenuEntry("Home", e -> {
			config.openHome();
		})
		.withMenuEntry("Settings", e -> {
			config.openPropertiesForEditing();
		})
		.withMenuEntry("Password", e -> {
			if(Password.showDialog()) {
				start();
			}
		})
		.withMenuEntry("Test", e -> {
			test();
		})
		.withMenuEntry("About", e -> {
			Version.openAbout();
		})
		.withMenuEntry("Restart", e -> {
			restart();
		})
		.withMenuEntry("Exit", e -> {
			log.info("Exiting...");
			System.exit(0);
		});
		Password.ui = ui;
		config.onPropertyChange(f -> {
			Config oldConfig = config;
			config = new Config().reload();
			if(!config.getUI().equals(oldConfig.getUI())) {
				// TODO can we recreate the UI without restarting the VM?
				String msg = "UI changed. Restarting proxy virtual machine.";
				log.info(msg);
				ui.displayMessage("Proxy restarting", msg);
				restart();
			} else if(!config.equals(oldConfig)) {
				start();
			} else {
				log.debug("Config did not change. Ignoring file change.");
			}
		});
	}

	private void restart(String ... args) {
		log.info("restarting");
		simpleProxyChain.stop();
		try {
			List<String> cmd = new ArrayList<>(asList(
					tool("java"), 
					"-cp",
					System.getProperty("java.class.path"),
					Proxy.class.getName()
					));
			cmd.addAll(asList(args));
			new ProcessBuilder(cmd)
				.inheritIO()
				.start();
		} catch (IOException e) {
			log.error("Could not start proxy", e);
			System.exit(667);
		}
		System.exit(0);
	}

	ProxyUI createUI() {
		switch (config.getUI()) {
			case AWT: return new ProxyUIAwt();
			default: return new ProxyUISwt();
		}
	} 
	
	public void start(String ... args) {
		final List<String> argList = asList(args);
		config.reload();
		try {
			if(config.useAuth()) Password.get();			
		} catch (IllegalStateException e) {
			Password.showDialog();
		}
		boolean restarting = simpleProxyChain != null;
		ui.show();				
		String startRestart = restarting ? "restarting ..." : "starting ...";
		log.info("proxy "+startRestart);
		ui.displayMessage("Proxy", startRestart);		
		checkProxyEnv();
		if(restarting) simpleProxyChain.stop();
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
			log.error(e.getMessage(), e);
			ui.showHTLM(false, "Start up failure", "<b>"+e.getCause().getMessage() +"</b><br/>Exiting.");
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e1) {
			}
			System.exit(666);
		}
		if(argList.contains(ARG_TEST)) {
			test();
		}
		log.info("proxy started");
	}
	
	public static void main(String[] args) throws IOException {
		try(InputStream logProps = Proxy.class.getResourceAsStream("logging.properties")){
			LogManager.getLogManager().readConfiguration(logProps);
		}
		new Proxy().start(args);
	}

	public boolean test() {
		String url = config.getTestURL();
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
		} catch (SSLHandshakeException e) {
			try {
				URL URL = new URL(url);
				Certificate cert = ImportTLSCert.getCertificate(URL.getHost(), getPort(URL), sa.getHostString(), sa.getPort());
				X509Certificate x509Certificate = (X509Certificate) cert;
				String msg = "Detected unstrusted proxy certificate";
				log.info(msg);
				if(ui.prompt(msg, format("Detected unstrusted proxy certificate from:\n %s\n\nDo you want to trust the certificate and restart the proxy?", x509Certificate.getIssuerX500Principal()))) {
					File certFile = ImportTLSCert.writeToFile(x509Certificate);
					certFile.deleteOnExit();
					String keystore = ImportTLSCert.getDefaultKeystore();
					ImportTLSCert.importCert(
							keystore,
							ImportTLSCert.getDefaultPassword(),
							ImportTLSCert.getDefaultAlias(x509Certificate),
							certFile.getAbsolutePath()
							);
					checkTrustStoreProperty(keystore);
					restart(ARG_TEST);
				}
			} catch (IOException | KeyManagementException | NoSuchAlgorithmException | CertificateEncodingException | InterruptedException e1) {
				log.error(e1.getMessage(), e1);
			}
			return false;
		} catch (IOException e) {
			log.warn(e.getMessage(), e);
			ui.displayMessage("Test on '"+url+"' failed", e.getMessage(), MessageType.ERROR);
			return false;
		}
	}

	private void checkProxyEnv() {
		if(config.checkEnvironment()) {
			String proxyHostProp = System.getProperty("https.proxyHost", System.getProperty("http.proxyHost"));
			String proxyPortProp = System.getProperty("https.proxyPort", System.getProperty("http.proxyPort"));
			String proxyHostEnv= "null";
			String proxyPortEnv = "null";
			final String thisProxyHost = "localhost";
			int thisProxyPort = config.getPort()[0];
			boolean dirtyProps = !Objects.equals(thisProxyHost, proxyHostProp) || proxyPortProp == null || !Objects.equals(thisProxyPort, Integer.valueOf(proxyPortProp).intValue());
			Optional<String> pEnv = Config.detectHTTPProxyEnv();
			boolean dirtyEnv = true;
			if(pEnv.isPresent()) {
				String[] httpProxyEnv = Config.parseHTTPProxyEnv(pEnv.get());
				proxyHostEnv = httpProxyEnv[0];
				proxyPortEnv = httpProxyEnv[1];
				if(thisProxyHost.equals(proxyHostEnv) && Objects.equals(thisProxyPort, Integer.valueOf(proxyPortEnv).intValue())) {
					dirtyEnv = false;
				}
			}
			if(dirtyProps || dirtyEnv) {
				String message = "";
				if(dirtyProps) {
					message += "Your JVM propoerties do not contain proxy settings.\n";
				}
				if(dirtyEnv) {
					message += "Your system environment does not contain proxy settings.\n";
				}
				message += "\nDo you want to update you JVM properties / system environment?";
				if(ui.prompt("Do you want to update you JVM properties / system environment?", message )) {
					if(dirtyProps) {
						updateJavaToolsOpts(Map.of("http.proxyHost", thisProxyHost, "https.proxyHost", thisProxyHost,"http.proxyPort", String.valueOf(thisProxyPort),"https.proxyPort", String.valueOf(thisProxyPort) ));
					}
					if(dirtyEnv) {
						String newProxyEnv = String.format("http://%s:%s", thisProxyHost, thisProxyPort);
						setEnv("http_proxy", newProxyEnv);
						setEnv("https_proxy", newProxyEnv);
						ui.displayMessage("Updated environment", format("Set http_proxy and https_proxy to\n%s",  newProxyEnv));
					}
				} else if(ui.prompt("Ignore differences", "Do you want disable detecting JVM property and system environment improvements on start up?" )) {
					config.setCheckEnvironment(false);
					ui.displayMessage("Ignoring  JVM property and system environment improvements", "Updated your settings in "+config.PROXY_PROPERTIES, MessageType.INFO);
				}
			}
		}
	}
	
	private void checkTrustStoreProperty(String keystore) {
		if(System.getProperty("javax.net.ssl.trustStore") == null) {
			if(canUpdateEnv()) {
				if(ui.prompt("Set trust store for all JVMs", 
						"The system property javax.net.ssl.trustStore is not set.\nDo you want to set it in the JAVA_TOOL_OPTIONS environment variable?\n(recommended)")) {
					updateJavaToolsOpts(Map.of("javax.net.ssl.trustStore", keystore));
				}
			} else {
				ui.displayMessage("The system property javax.net.ssl.trustStore is not set", "We recommend to set the environment variable JAVA_TOOL_OPTIONS so that it contains:\n -Djavax.net.ssl.trustStore="+keystore, MessageType.WARNING);
			}
		} 
	}


	private void setEnv(String key, String value) {
		if(!canUpdateEnv()) throw new IllegalStateException("I do notknow how to set environment variables on "+ OperatingSystem.CURRENT);
		log.info(format("Updating environment %s from %s to %s.", key, System.getenv(key), value));
		switch (OperatingSystem.CURRENT) {
			case WINDOWS:
				try {
					new ProcessBuilder("setx", key, value).start();
				} catch (IOException e) {
					log.error("Could not update environment", e);
				}
				break;
			case MAC:
			case LINUX:
				break;
			default:
				break;
		}
	}

	private boolean canUpdateEnv() {
		switch (OperatingSystem.CURRENT) {
		case WINDOWS:
			return true;
		case MAC:
		case LINUX:
			return new File("~/.bashrc").exists();
		default:
			return false;
		}
	}

	private int getPort(URL url) {
		return url.getPort() > 0 ? url.getPort() : url.getDefaultPort();
	}
	
	private void updateJavaToolsOpts(Map<String, String> options) {
		String toolsOptOld = System.getenv("JAVA_TOOL_OPTIONS");
		Map<String, String> toolOptions = parseToolOptions(toolsOptOld);
		toolOptions.putAll(options);
		String toolsOptNew = generateToolOptions(toolOptions);
		setEnv("JAVA_TOOL_OPTIONS", toolsOptNew);
		ui.displayMessage("Updated JAVA_TOOL_OPTIONS", format("Updated JAVA_TOOL_OPTIONS from \n%s\nto\n%s",  toolsOptOld, toolsOptNew));
	}
	
	static Map<String, String> parseToolOptions(String options) {
		Map<String, String> parsed = new HashMap<>();
		if(options==null || options.isBlank()) return parsed;
		String replaceFirst = options.replaceFirst("^\"", "").replaceFirst("\"$", "");
		try(Scanner scan = new Scanner(replaceFirst)) {
			while (scan.hasNext()) {
				String[] kv = scan.next().split("=");
				parsed.put(kv[0].substring(2), kv[1]);
			}
			return parsed;			
		}
	}
	
	static String generateToolOptions(Map<String, String> options) {
		return options.entrySet().stream()
				.sorted((e1,e2)-> e1.getKey().compareTo(e2.getKey()))
				.map(e-> String.format("-D%s=%s", e.getKey(), e.getValue())).collect(Collectors.joining(" "));
	}
	
	
}
