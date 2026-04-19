package com.baloise.proxy.config;

import static com.baloise.proxy.config.Config.parseHTTPProxyEnv;
import static com.baloise.proxy.config.Config.parseIntArray;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class ConfigTest {

	@Test
	void parseIntArray_single() {
		assertArrayEquals(new int[]{8888}, parseIntArray("8888"));
	}

	@Test
	void parseIntArray_multiple() {
		assertArrayEquals(new int[]{8888, 3128}, parseIntArray("8888, 3128"));
	}

	@Test
	void parseIntArray_dirtyInputStillExtractsDigits() {
		assertArrayEquals(new int[]{8888, 3128}, parseIntArray("8888 lksdlf; <>, 3128"));
	}

	@Test
	void parseIntArray_noDigitsReturnsEmpty() {
		assertArrayEquals(new int[]{}, parseIntArray("sdfsdf"));
	}

	@Test
	void parseIntArray_nullReturnsEmpty() {
		assertArrayEquals(new int[]{}, parseIntArray(null));
	}

	@Test
	void parseHTTPProxyEnv_stripsScheme() {
		assertArrayEquals(new String[]{"proxy.example.com", "8080"}, parseHTTPProxyEnv("http://proxy.example.com:8080"));
		assertArrayEquals(new String[]{"proxy.example.com", "8080"}, parseHTTPProxyEnv("HTTPS://proxy.example.com:8080"));
		assertArrayEquals(new String[]{"proxy.example.com", "8080"}, parseHTTPProxyEnv("proxy.example.com:8080"));
	}
}
