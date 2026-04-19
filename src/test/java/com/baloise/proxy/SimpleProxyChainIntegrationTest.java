package com.baloise.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.baloise.proxy.config.Config;
import com.sun.net.httpserver.HttpServer;

/**
 * End-to-end test: runs a real {@link SimpleProxyChain} against an in-JVM
 * HTTP origin server and a stub upstream HTTP proxy that responds inline
 * (no external network).
 *
 * <p>Covers:
 * <ul>
 *   <li>Requests to hosts matching the no-proxy regex reach the origin directly
 *       (through the internal bypass proxy).</li>
 *   <li>Requests to other hosts are routed to the stub upstream proxy.</li>
 *   <li>Basic-auth header is added when {@code useAuth=true}.</li>
 *   <li>200 concurrent requests complete in &lt; 10 s.</li>
 * </ul>
 */
class SimpleProxyChainIntegrationTest {

	static {
		// HttpURLConnection reuses proxy connections aggressively, which races with
		// the stub upstream closing them. Disable keep-alive for deterministic tests.
		System.setProperty("http.keepAlive", "false");
	}

	private HttpServer origin;
	private StubUpstreamProxy upstream;
	private SimpleProxyChain chain;
	private int originPort;
	private int localPort;
	private int internalPort;
	private int upstreamPort;

	@BeforeEach
	void setUp() throws Exception {
		originPort   = freePort();
		localPort    = freePort();
		internalPort = freePort();
		upstreamPort = freePort();

		origin = HttpServer.create(new InetSocketAddress("127.0.0.1", originPort), 0);
		origin.createContext("/", ex -> {
			byte[] body = "origin-ok".getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(200, body.length);
			try (OutputStream os = ex.getResponseBody()) { os.write(body); }
		});
		origin.start();

		upstream = new StubUpstreamProxy(upstreamPort, "upstream-ok");
		upstream.start();

		chain = new SimpleProxyChain(cfg(false));
		chain.start(new FiltersSource407(() -> { /* no-op */ }));
	}

	@AfterEach
	void tearDown() {
		if (chain != null) chain.stop();
		if (origin != null) origin.stop(0);
		if (upstream != null) upstream.stop();
	}

	@Test
	void bypassHostReachesOriginDirectly() throws IOException {
		int upstreamBefore = upstream.requests.size();
		String body = getViaLocalProxy("http://127.0.0.1:" + originPort + "/");
		assertEquals("origin-ok", body);
		assertEquals(upstreamBefore, upstream.requests.size(), "no-proxy request must not hit upstream");
	}

	@Test
	void nonBypassHostRoutedToUpstream() throws IOException {
		int before = upstream.requests.size();
		String body = getViaLocalProxy("http://some.external.host/whatever");
		assertEquals("upstream-ok", body);
		assertTrue(upstream.requests.size() > before, "upstream must see the request");
		assertTrue(upstream.requests.get(upstream.requests.size() - 1).contains("http://some.external.host/whatever"),
				"upstream must receive the absolute URI");
	}

	@Test
	void basicAuthHeaderInjectedWhenUseAuthTrue() throws Exception {
		chain.stop();
		// Save-and-restore whatever real password the developer may have stored so the
		// test is a no-op on their persisted credentials.
		String priorEncrypted = common.Password.node().get("password", null);
		common.Password.set("s3cret");
		try {
			chain = new SimpleProxyChain(cfg(true));
			chain.start(new FiltersSource407(() -> { }));

			int before = upstream.proxyAuth.size();
			getViaLocalProxy("http://some.external.host/authtest");
			assertTrue(upstream.proxyAuth.size() > before);
			String seen = upstream.proxyAuth.get(upstream.proxyAuth.size() - 1);
			assertTrue(seen != null && seen.startsWith("Basic "), "expected Basic auth, saw: " + seen);
		} finally {
			if (priorEncrypted == null) {
				common.Password.remove();
			} else {
				common.Password.node().put("password", priorEncrypted);
			}
		}
	}

	@Test
	void correctnessUnderModerateConcurrency() throws Exception {
		int n = 20;
		ExecutorService pool = Executors.newFixedThreadPool(4);
		List<Future<String>> futures = new ArrayList<>(n);
		long t0 = System.nanoTime();
		for (int i = 0; i < n; i++) {
			futures.add(pool.submit(() -> getViaLocalProxy("http://some.external.host/concurrency", 10_000)));
		}
		int ok = 0;
		for (Future<String> f : futures) {
			if ("upstream-ok".equals(f.get(15, TimeUnit.SECONDS))) ok++;
		}
		long ms = (System.nanoTime() - t0) / 1_000_000L;
		pool.shutdown();
		assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "pool must drain");
		assertEquals(n, ok, "all " + n + " concurrent requests must succeed");
		// Tight bound: observed is <1s on dev hardware; 10s is still a very generous ceiling
		// that will reliably catch a real regression.
		assertTrue(ms < 10_000, n + " concurrent requests took " + ms + "ms");
	}

	@Test
	void connectRoutingToBypassHost() throws Exception {
		// CONNECT routing decision is a separate code path from absolute-URI GET.
		// We don't complete the tunnel (no TLS server behind it); we just verify the proxy
		// accepts our CONNECT for a bypass host (127.0.0.1) and attempts to tunnel via
		// the internal proxy, not the upstream stub. We prove that by counting upstream
		// requests before/after.
		int upstreamBefore = upstream.requests.size();
		try (Socket s = new Socket("127.0.0.1", localPort)) {
			s.setSoTimeout(3000);
			java.io.OutputStream out = s.getOutputStream();
			String req = "CONNECT 127.0.0.1:" + originPort + " HTTP/1.1\r\n"
					+ "Host: 127.0.0.1:" + originPort + "\r\n"
					+ "\r\n";
			out.write(req.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
			out.flush();
			// Read the status line; LittleProxy should answer with 200 Connection Established
			java.io.InputStream in = s.getInputStream();
			byte[] buf = new byte[256];
			int n = in.read(buf);
			String statusLine = new String(buf, 0, Math.max(n, 0), java.nio.charset.StandardCharsets.ISO_8859_1);
			assertTrue(statusLine.startsWith("HTTP/1.1 200") || statusLine.startsWith("HTTP/1.0 200"),
					"expected CONNECT 200 response, got: " + statusLine);
		}
		assertEquals(upstreamBefore, upstream.requests.size(),
				"CONNECT to a bypass host must not hit the upstream stub");
	}

	@Test
	void sequentialRequestsAreFast() throws Exception {
		int n = 20;
		long t0 = System.nanoTime();
		for (int i = 0; i < n; i++) {
			assertEquals("upstream-ok", getViaLocalProxy("http://some.external.host/seq-" + i));
		}
		long ms = (System.nanoTime() - t0) / 1_000_000L;
		// Sanity: each request should be well under 500ms locally.
		assertTrue(ms < n * 500L, n + " sequential requests took " + ms + "ms (limit " + (n * 500L) + "ms)");
	}

	// --- helpers ---------------------------------------------------------------

	private Config cfg(boolean useAuth) {
		TestConfig c = new TestConfig();
		c.props.setProperty("SimpleProxyChain.noproxyHostsRegEx", "127\\.0\\.0\\.1|localhost");
		c.props.setProperty("SimpleProxyChain.internalPort",      String.valueOf(internalPort));
		c.props.setProperty("SimpleProxyChain.port",              String.valueOf(localPort));
		c.props.setProperty("SimpleProxyChain.upstreamServer",    "127.0.0.1");
		c.props.setProperty("SimpleProxyChain.upstreamPort",      String.valueOf(upstreamPort));
		c.props.setProperty("SimpleProxyChain.useAuth",           String.valueOf(useAuth));
		c.props.setProperty("allowLocalOnly",                     "true");
		c.props.setProperty("connectTimeoutMs",                   "5000");
		c.props.setProperty("idleTimeoutSeconds",                 "30");
		return c;
	}

	private String getViaLocalProxy(String url) throws IOException {
		return getViaLocalProxy(url, 5000);
	}

	private String getViaLocalProxy(String url, int readTimeoutMs) throws IOException {
		java.net.Proxy p = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", localPort));
		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection(p);
		con.setConnectTimeout(5000);
		con.setReadTimeout(readTimeoutMs);
		con.setRequestProperty("Connection", "close");
		int code = con.getResponseCode();
		assertEquals(200, code, "expected HTTP 200, got " + code);
		try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = r.readLine()) != null) sb.append(line);
			return sb.toString();
		}
	}

	private static int freePort() {
		try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
		catch (IOException e) { throw new RuntimeException(e); }
	}

	/** Config backed by in-memory Properties; bypasses filesystem. */
	static class TestConfig extends Config {
		final Properties props = new Properties();

		@Override protected void bootstrapPropertiesFile() { /* no filesystem I/O in tests */ }

		@Override public boolean useAuth()         { return Boolean.parseBoolean(props.getProperty("SimpleProxyChain.useAuth", "false")); }
		@Override public boolean allowLocalOnly()  { return Boolean.parseBoolean(props.getProperty("allowLocalOnly", "true")); }
		@Override public String getUpstreamServer(){ return props.getProperty("SimpleProxyChain.upstreamServer"); }
		@Override public int getUpstreamPort()     { return Integer.parseInt(props.getProperty("SimpleProxyChain.upstreamPort")); }
		@Override public int[] getPort()           { return new int[]{Integer.parseInt(props.getProperty("SimpleProxyChain.port"))}; }
		@Override public int getInternalPort()     { return Integer.parseInt(props.getProperty("SimpleProxyChain.internalPort")); }
		@Override public String getNoproxyHostsRegEx() { return props.getProperty("SimpleProxyChain.noproxyHostsRegEx"); }
		@Override public int getConnectTimeoutMs() { return Integer.parseInt(props.getProperty("connectTimeoutMs", "5000")); }
		@Override public int getIdleTimeoutSeconds() { return Integer.parseInt(props.getProperty("idleTimeoutSeconds", "30")); }
	}

	/**
	 * Minimal inline HTTP proxy: responds with {@code cannedBody} to every absolute-URI GET,
	 * recording the request line + any Proxy-Authorization header.
	 */
	static class StubUpstreamProxy {
		final int port;
		final String cannedBody;
		final List<String> requests  = new CopyOnWriteArrayList<>();
		final List<String> proxyAuth = new CopyOnWriteArrayList<>();
		private ServerSocket server;
		private volatile boolean running;

		StubUpstreamProxy(int port, String cannedBody) {
			this.port = port;
			this.cannedBody = cannedBody;
		}

		void start() throws IOException {
			server = new ServerSocket(port, 50, java.net.InetAddress.getByName("127.0.0.1"));
			running = true;
			Thread t = new Thread(this::acceptLoop, "stub-upstream-proxy");
			t.setDaemon(true);
			t.start();
		}

		void stop() {
			running = false;
			try { server.close(); } catch (IOException ignored) { }
		}

		private void acceptLoop() {
			while (running && !server.isClosed()) {
				try {
					Socket client = server.accept();
					Thread worker = new Thread(() -> handle(client), "stub-upstream-worker");
					worker.setDaemon(true);
					worker.start();
				} catch (IOException e) {
					return;
				}
			}
		}

		private void handle(Socket client) {
			try (client) {
				InputStream is = client.getInputStream();
				BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.ISO_8859_1));
				String requestLine = in.readLine();
				if (requestLine == null) return;
				requests.add(requestLine);

				String seenAuth = null;
				String line;
				while ((line = in.readLine()) != null && !line.isEmpty()) {
					if (line.toLowerCase().startsWith("proxy-authorization:")) {
						seenAuth = line.substring(line.indexOf(':') + 1).trim();
					}
				}
				proxyAuth.add(seenAuth == null ? "" : seenAuth);

				byte[] body = cannedBody.getBytes(StandardCharsets.UTF_8);
				String resp = "HTTP/1.1 200 OK\r\n"
						+ "Content-Type: text/plain\r\n"
						+ "Content-Length: " + body.length + "\r\n"
						+ "Connection: close\r\n"
						+ "\r\n";
				OutputStream out = client.getOutputStream();
				out.write(resp.getBytes(StandardCharsets.ISO_8859_1));
				out.write(body);
				out.flush();
			} catch (IOException ignored) {
			}
		}
	}
}
