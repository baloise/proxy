package com.baloise.proxy;

import static com.baloise.proxy.SimpleProxyChain.extractHost;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Covers the hot-path URI parser used for the no-proxy routing decision.
 * Regression-critical: a broken extractHost routes every request to the wrong chain.
 */
class SimpleProxyChainHostTest {

	@Test
	void httpAbsoluteUri() {
		assertEquals("example.com", extractHost("http://example.com/path"));
	}

	@Test
	void httpsAbsoluteUri() {
		assertEquals("example.com", extractHost("https://example.com/path"));
	}

	@Test
	void absoluteUriWithPort() {
		assertEquals("example.com", extractHost("http://example.com:8080/path"));
	}

	@Test
	void absoluteUriWithQuery() {
		assertEquals("api.example.com", extractHost("https://api.example.com/v1/users?id=42"));
	}

	@Test
	void absoluteUriNoPath() {
		assertEquals("example.com", extractHost("http://example.com"));
	}

	@Test
	void connectAuthority() {
		assertEquals("example.com", extractHost("example.com:443"));
	}

	@Test
	void connectAuthorityNoPort() {
		assertEquals("example.com", extractHost("example.com"));
	}

	@Test
	void ipv6LiteralInConnect() {
		assertEquals("::1", extractHost("[::1]:443"));
	}

	@Test
	void ipv6LiteralInAbsolute() {
		assertEquals("2001:db8::1", extractHost("https://[2001:db8::1]:8443/"));
	}

	@Test
	void ipv4Literal() {
		assertEquals("127.0.0.1", extractHost("http://127.0.0.1:8888/api"));
	}

	@Test
	void userInfoInUriKeepsUsername() {
		// RFC 7230 forbids userinfo in proxy request URIs, but clients sometimes send it.
		// Document the current behavior so a refactor doesn't silently change it.
		assertEquals("user", extractHost("http://user:pass@host.com/"));
	}

	@Test
	void fragmentWithoutPath() {
		// Unusual but legal; should not throw.
		assertEquals("host#frag", extractHost("http://host#frag"));
	}

	@Test
	void emptyUriReturnsEmpty() {
		// Must not throw. noProxy regex will miss, request gets routed to upstream.
		assertEquals("", extractHost(""));
	}

	@Test
	void malformedSchemeOnlyUri() {
		// Leading "://" with no scheme: the parser treats the first ':' as a port separator
		// and returns empty. Not a standards-compliant URI anyway; we assert we don't throw.
		assertEquals("", extractHost("://oops"));
	}

	@Test
	void corporateNoProxyRegexMatchesInternal() {
		Pattern p = Pattern.compile(".*((baloise|baloisenet|balgroupit)\\.com|bvch\\.ch|localhost|127\\.0\\.0\\.1)\\Z");
		// direct
		assertMatches(p, extractHost("http://webmail.baloise.com/"));
		assertMatches(p, extractHost("https://intranet.balgroupit.com/some/path"));
		assertMatches(p, extractHost("localhost:8080"));
		assertMatches(p, extractHost("127.0.0.1:9090"));
		// upstream
		assertDoesNotMatch(p, extractHost("https://github.com/foo"));
		assertDoesNotMatch(p, extractHost("https://api.openai.com/v1/chat"));
	}

	private static void assertMatches(Pattern p, String input) {
		if (!p.matcher(input).matches()) throw new AssertionError(input + " should match " + p);
	}

	private static void assertDoesNotMatch(Pattern p, String input) {
		if (p.matcher(input).matches()) throw new AssertionError(input + " should NOT match " + p);
	}
}
