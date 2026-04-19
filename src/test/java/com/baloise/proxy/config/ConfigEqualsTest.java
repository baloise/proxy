package com.baloise.proxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link Config#equals(Object)} must compare <i>effective</i> values, not raw
 * properties. Cosmetic edits (comments, whitespace, key reordering) must not
 * trigger a proxy restart, but real value changes must.
 *
 * <p>The test flips {@code user.home} to a temporary directory so the tested
 * {@link Config} reads from {@code <tmp>/.proxy/proxy.properties}.
 */
class ConfigEqualsTest {

	private String originalUserHome;

	@BeforeEach
	void setUp() {
		originalUserHome = System.getProperty("user.home");
	}

	@AfterEach
	void tearDown() {
		if (originalUserHome != null) System.setProperty("user.home", originalUserHome);
	}

	@Test
	void cosmeticEditsAreEqual(@TempDir Path tmp) throws IOException {
		Config a = loadFresh(tmp, """
				# a comment
				SimpleProxyChain.port=8888
				SimpleProxyChain.upstreamServer=127.0.0.1
				""");
		Config b = loadFresh(tmp, """
				SimpleProxyChain.upstreamServer=127.0.0.1
				# reordered
				SimpleProxyChain.port=8888

				""");
		assertEquals(a, b, "reordering + comments must not count as a config change");
		assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	void realPortChangeIsNotEqual(@TempDir Path tmp) throws IOException {
		Config a = loadFresh(tmp, "SimpleProxyChain.port=8888\nSimpleProxyChain.upstreamServer=127.0.0.1\nSimpleProxyChain.upstreamPort=3128\n");
		Config b = loadFresh(tmp, "SimpleProxyChain.port=9999\nSimpleProxyChain.upstreamServer=127.0.0.1\nSimpleProxyChain.upstreamPort=3128\n");
		assertNotEquals(a, b);
	}

	private Config loadFresh(Path tmp, String contents) throws IOException {
		System.setProperty("user.home", tmp.toString());
		Path proxyDir = tmp.resolve(".proxy");
		Files.createDirectories(proxyDir);
		Files.writeString(proxyDir.resolve("proxy.properties"), contents);
		return new Config().reload();
	}
}
