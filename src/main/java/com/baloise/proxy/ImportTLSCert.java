package com.baloise.proxy;
import static java.io.File.separator;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.util.Arrays.asList;
import static java.util.regex.Pattern.quote;
import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

// for a more comprehensive solution see http://keystore-explorer.org/
public class ImportTLSCert {

	private static final String JAVAX_NET_SSL_TRUST_STORE = "javax.net.ssl.trustStore";

	public static String jre() {
		try {
			RuntimeMXBean mxbean = ManagementFactory.getPlatformMXBean(RuntimeMXBean.class);
			return mxbean.getBootClassPath().split(quote(separator) + "lib" + quote(separator) + "\\w+\\.jar", 2)[0];
		} catch (UnsupportedOperationException e) {
			String candidate = System.getProperty("java.home");
			if(candidate != null) return candidate;
			candidate = System.getProperty("sun.boot.library.path");
			if(candidate != null) return candidate.split(quote(separator)+"jre")[0]+separator+"jre";
			throw new IllegalStateException("Could not determine jre location");
		}
	}

	public static String tool(String tool) {
		String java = jre() + separator + "bin" + separator + tool;
		return new File(java).exists() ? java : java + ".exe";
	}

	static Certificate getCertificate(String hostName, int port, String proxyHost, int proxyPort) throws NoSuchAlgorithmException,
			KeyManagementException, IOException, UnknownHostException, SSLPeerUnverifiedException {
		String trustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
		System.setProperty(JAVAX_NET_SSL_TRUST_STORE, "clienttrust");
		try {
			SSLContext ctx = SSLContext.getInstance("TLS");
			X509TrustManager tm = new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
				}
	
				public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {
				}
	
				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
			};
			ctx.init(null, new TrustManager[] { tm }, null);
			SSLSocketFactory ssf = ctx.getSocketFactory();
			Socket socket;
			if (proxyHost.isEmpty()) {
				socket = ssf.createSocket(hostName, port);
			} else {
				Socket tunnel = new Socket(proxyHost, proxyPort);
				doTunnelHandshake(tunnel, hostName, port);
				socket = (SSLSocket) ssf.createSocket(tunnel, hostName, port, true);
			}
	
			Certificate[] cchain = ((SSLSocket) socket).getSession().getPeerCertificates();
			return cchain[cchain.length - 1];
		} finally {
			if(trustStore == null) {
				System.clearProperty(JAVAX_NET_SSL_TRUST_STORE);
			} else {
				System.setProperty(JAVAX_NET_SSL_TRUST_STORE, trustStore);
			}
			
		}
	}

	static Scanner scanIn = new Scanner(System.in);

	public static void main(String[] args) throws Exception {
		String host = readInput("HTTPS host", "example.com");
		int port = Integer.valueOf(readInput("HTTPS port", "443"));
		String proxyHost = readInput("proxy host (leave empty to disable proxy)", "");
		int proxyPort = proxyHost.isEmpty() ? -1 :  Integer.valueOf(readInput("proxy port", "8888"));
		Certificate cert = getCertificate(host, port, proxyHost, proxyPort);
		X509Certificate x509Certificate = (X509Certificate) cert;
		System.out.println(x509Certificate.getSubjectX500Principal());
		System.out.println();
		String alias = readInput("alias", getDefaultAlias(x509Certificate));
		String keystore = readInput("keystore", getDefaultKeystore());
		System.out.println("the default password of the JVM is 'changeit'");
		String storePass = readInput("storePass", getDefaultPassword());

		File certFile = writeToFile(x509Certificate);
		certFile.deleteOnExit();
		importCert(keystore, storePass, alias, certFile.getAbsolutePath());
	}

	public static String getDefaultPassword() {
		return "changeit";
	}

	public static String getDefaultAlias(X509Certificate x509Certificate) {
		return x509Certificate.getSubjectX500Principal().getName().replaceAll("\\W", "");
	}

	public static String getDefaultKeystore() {
		return getProperty(JAVAX_NET_SSL_TRUST_STORE, jre() + "/lib/security/cacerts".replace("/", separator));
	}

	static File writeToFile(Certificate certificate) throws IOException, CertificateEncodingException {
		final String NL = System.getProperty("line.separator");
		File ret = File.createTempFile("cert", ".txt");
		String encoded = Base64.getMimeEncoder(64, NL.getBytes()).encodeToString(certificate.getEncoded());
		Files.write(ret.toPath(),
				format("-----BEGIN CERTIFICATE-----%s%s%s-----END CERTIFICATE-----", NL, encoded, NL).getBytes());
		return ret;
	}

	private static String readInput(String message, String defaultValue) {
		System.out.println(format("%s [%s]:", message, defaultValue));
		String ret = scanIn.nextLine().trim();
		return ret.isEmpty() ? defaultValue : ret;
	}

	static void importCert(String keystore, String storePass, String alias, String certFile)
			throws InterruptedException, IOException {
		List<String> cmd = asList(tool("keytool"), "-import", "-alias", alias, "-keystore", keystore, "-file", certFile,
				"-storePass", storePass, "-noprompt", "-v");
		System.out.println(cmd.stream().collect(joining(" ")));
		new ProcessBuilder(cmd).inheritIO().start().waitFor();
	}

	private static void doTunnelHandshake(Socket tunnel, String host, int port) throws IOException {
		//see https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/samples/sockets/client/SSLSocketClientWithTunneling.java
		OutputStream out = tunnel.getOutputStream();
		String msg = "CONNECT " + host + ":" + port + " HTTP/1.1\n" + "User-Agent: Java \r\n\r\n";
		byte b[];
		try {
			/*
			 * We really do want ASCII7 -- the http protocol doesn't change with locale.
			 */
			b = msg.getBytes("ASCII7");
		} catch (UnsupportedEncodingException ignored) {
			/*
			 * If ASCII7 isn't there, something serious is wrong, but Paranoia Is Good (tm)
			 */
			b = msg.getBytes();
		}
		out.write(b);
		out.flush();

		/*
		 * We need to store the reply so we can create a detailed error message to the
		 * user.
		 */
		byte reply[] = new byte[200];
		int replyLen = 0;
		int newlinesSeen = 0;
		boolean headerDone = false; /* Done on first newline */

		InputStream in = tunnel.getInputStream();

		while (newlinesSeen < 2) {
			int i = in.read();
			if (i < 0) {
				throw new IOException("Unexpected EOF from proxy");
			}
			if (i == '\n') {
				headerDone = true;
				++newlinesSeen;
			} else if (i != '\r') {
				newlinesSeen = 0;
				if (!headerDone && replyLen < reply.length) {
					reply[replyLen++] = (byte) i;
				}
			}
		}

		/*
		 * Converting the byte array to a string is slightly wasteful in the case where
		 * the connection was successful, but it's insignificant compared to the network
		 * overhead.
		 */
		String replyStr;
		try {
			replyStr = new String(reply, 0, replyLen, "ASCII7");
		} catch (UnsupportedEncodingException ignored) {
			replyStr = new String(reply, 0, replyLen);
		}

		/* We asked for HTTP/1.0, so we should get that back */
		if (!replyStr.startsWith("HTTP/1.1 200")) {
			throw new IOException("Unable to tunnel open tunnel.  Proxy returns \""
					+ replyStr + "\"");
		}

		/* tunneling Handshake was successful! */
	}
}
