package com.baloise.proxy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Boots the full {@link Proxy} (including tray UI setup, but in headless mode)
 * and asserts the listen port is actually accepting connections. The smoke test
 * for the orchestration class that is otherwise uncovered.
 */
class ProxySmokeTest {

	private String originalUserHome;
	private String originalHeadless;

	@BeforeEach
	void setUp() {
		originalUserHome = System.getProperty("user.home");
		originalHeadless = System.getProperty("java.awt.headless");
		System.setProperty("java.awt.headless", "true");
	}

	@AfterEach
	void tearDown() {
		if (originalUserHome != null) System.setProperty("user.home", originalUserHome);
		if (originalHeadless == null) System.clearProperty("java.awt.headless");
		else System.setProperty("java.awt.headless", originalHeadless);
	}

	@Test
	void bootsAndListensOnConfiguredPort(@TempDir Path tmp) throws IOException {
		System.setProperty("user.home", tmp.toString());
		Path proxyDir = tmp.resolve(".proxy");
		Files.createDirectories(proxyDir);
		int port = freePort();
		int internalPort = freePort();
		Files.writeString(proxyDir.resolve("proxy.properties"),
				"SimpleProxyChain.port=" + port + "\n" +
				"SimpleProxyChain.internalPort=" + internalPort + "\n" +
				"SimpleProxyChain.upstreamServer=127.0.0.1\n" +
				"SimpleProxyChain.upstreamPort=1\n" +
				"SimpleProxyChain.useAuth=false\n" +
				"SimpleProxyChain.noproxyHostsRegEx=.*\n" +
				"allowLocalOnly=true\n" +
				"connectTimeoutMs=5000\n" +
				"idleTimeoutSeconds=30\n");

		Proxy proxy = new Proxy();
		try {
			proxy.start();
			assertTrue(isListening("127.0.0.1", port),
					"Proxy must listen on configured port " + port);
			assertTrue(isListening("127.0.0.1", internalPort),
					"Internal proxy must listen on port " + internalPort);
		} finally {
			// Proxy has no public stop, but the test's JVM lives on - the SimpleProxyChain
			// threads are daemons and will die with the test JVM. Explicit cleanup via
			// reflection would be overkill for a smoke test.
		}
	}

	private static boolean isListening(String host, int port) {
		try (Socket s = new Socket()) {
			s.connect(new InetSocketAddress(host, port), 1000);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static int freePort() throws IOException {
		try (java.net.ServerSocket s = new java.net.ServerSocket(0)) { return s.getLocalPort(); }
	}
}
