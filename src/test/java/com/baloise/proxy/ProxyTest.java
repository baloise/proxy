package com.baloise.proxy;

import static com.baloise.proxy.Proxy.generateToolOptions;
import static com.baloise.proxy.Proxy.parseToolOptions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class ProxyTest {


	
	@Test
	public void testParseToolOptions() throws Exception {
		Map<String, String> expected = Map.of("javax.net.ssl.trustStore","C:/Users/Public/dev/java/lib/cacerts","http.proxyHost", "localhost","http.proxyPort","8888","https.proxyHost","localhost", "https.proxyPort", "8888");
		assertTrue(expected.equals(parseToolOptions("-Djavax.net.ssl.trustStore=C:/Users/Public/dev/java/lib/cacerts -Dhttp.proxyHost=localhost -Dhttp.proxyPort=8888 -Dhttps.proxyHost=localhost -Dhttps.proxyPort=8888")));
		assertTrue(expected.equals(parseToolOptions("\"-Djavax.net.ssl.trustStore=C:/Users/Public/dev/java/lib/cacerts -Dhttp.proxyHost=localhost -Dhttp.proxyPort=8888 -Dhttps.proxyHost=localhost -Dhttps.proxyPort=8888\"")));
		assertTrue(Map.of().equals(parseToolOptions("")));
		assertTrue(Map.of().equals(parseToolOptions(null)));
	}
	
	@Test
	public void testGenerateToolOptions() throws Exception {
		Map<String, String> options = Map.of("javax.net.ssl.trustStore","C:/Users/Public/dev/java/lib/cacerts","http.proxyHost", "localhost","http.proxyPort","8888","https.proxyHost","localhost", "https.proxyPort", "8888");
		assertEquals("-Dhttp.proxyHost=localhost -Dhttp.proxyPort=8888 -Dhttps.proxyHost=localhost -Dhttps.proxyPort=8888 -Djavax.net.ssl.trustStore=C:/Users/Public/dev/java/lib/cacerts", generateToolOptions(options));
	}

}
