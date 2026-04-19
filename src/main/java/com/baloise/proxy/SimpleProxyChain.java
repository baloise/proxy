package com.baloise.proxy;

import static java.util.stream.Collectors.toList;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.littleshoot.proxy.ChainedProxy;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.ChainedProxyManager;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.ClientDetails;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baloise.proxy.config.Config;

import common.BasicAuth;
import common.User;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Fans requests out to either the upstream authenticating proxy or a local bypass proxy
 * depending on the no-proxy regex. Reads credentials through a cached volatile field so
 * runtime password changes can be applied via {@link #invalidateAuthCache()} without
 * recreating the chain.
 *
 * <p>Hot-path invariants (every request goes through these):
 * <ul>
 *   <li>{@link #extractHost(String)} is allocation-free on the common cases.</li>
 *   <li>Both {@link InetSocketAddress} targets are built once at construction and reused.</li>
 *   <li>The auth header is cached between password invalidations.</li>
 *   <li>Logging in the routing callback is guarded by {@code isDebugEnabled()}.</li>
 * </ul>
 */
public class SimpleProxyChain {

	private static final Logger log = LoggerFactory.getLogger(SimpleProxyChain.class);

	private final String upstreamServer;
	private final int upstreamPort;
	public final int[] localPorts;
	private final int internalPort;
	private final boolean allowLocalOnly;
	private final boolean useAuth;
	private final int connectTimeoutMs;
	private final int idleTimeoutSeconds;
	private final Pattern noProxyPattern;
	private final ChainedProxyManager chainedProxyManager;

	/** Fully-formed header value, e.g. "Basic dXNlcjpwdw==". */
	private volatile String cachedAuthHeaderValue;
	private HttpProxyServer internalProxy;
	private List<HttpProxyServer> localProxies;

	public SimpleProxyChain(Config config) {
		this.upstreamServer = config.getUpstreamServer();
		this.upstreamPort = config.getUpstreamPort();
		this.localPorts = config.getPort();
		this.internalPort = config.getInternalPort();
		this.allowLocalOnly = config.allowLocalOnly();
		this.useAuth = config.useAuth();
		this.connectTimeoutMs = config.getConnectTimeoutMs();
		this.idleTimeoutSeconds = config.getIdleTimeoutSeconds();
		this.noProxyPattern = Pattern.compile(config.getNoproxyHostsRegEx());

		log.info("{} user={}", this, User.get());

		// Reuse these addresses across every request.
		final InetSocketAddress upstreamAddr = InetSocketAddress.createUnresolved(upstreamServer, upstreamPort);
		final InetSocketAddress loopbackAddr = InetSocketAddress.createUnresolved("localhost", internalPort);

		// When useAuth is false, drop filterRequest entirely - no per-request boolean check.
		final ChainedProxyAdapter webproxy = useAuth
				? new ChainedProxyAdapter() {
					@Override public InetSocketAddress getChainedProxyAddress() { return upstreamAddr; }
					@Override public void filterRequest(HttpObject httpObject) {
						if (httpObject instanceof HttpRequest) {
							((HttpRequest) httpObject).headers().set(HttpHeaderNames.PROXY_AUTHORIZATION, authHeaderValue());
						}
					}
				}
				: new ChainedProxyAdapter() {
					@Override public InetSocketAddress getChainedProxyAddress() { return upstreamAddr; }
				};

		final ChainedProxyAdapter noProxy = new ChainedProxyAdapter() {
			@Override
			public InetSocketAddress getChainedProxyAddress() {
				return loopbackAddr;
			}
		};

		this.chainedProxyManager = new ChainedProxyManager() {
			@Override
			public void lookupChainedProxies(HttpRequest request, Queue<ChainedProxy> chain, ClientDetails client) {
				boolean bypass = noProxyPattern.matcher(extractHost(request.getUri())).matches();
				if (log.isDebugEnabled()) {
					log.debug("{} {} -> {}", request.getMethod(), request.getUri(), bypass ? "direct" : "upstream");
				}
				chain.add(bypass ? noProxy : webproxy);
			}
		};
	}

	/** Invalidate cached credentials (call after password change). */
	public void invalidateAuthCache() {
		cachedAuthHeaderValue = null;
	}

	private String authHeaderValue() {
		String v = cachedAuthHeaderValue;
		if (v == null) {
			v = "Basic " + BasicAuth.get();
			cachedAuthHeaderValue = v;
		}
		return v;
	}

	/**
	 * Extract the host part of a proxy request URI. Handles:
	 * <ul>
	 *   <li>{@code http://host:port/path} (HTTP)</li>
	 *   <li>{@code https://host:port/path}</li>
	 *   <li>{@code host:port} (CONNECT tunnel)</li>
	 *   <li>{@code [::1]:443} (IPv6 literal in CONNECT)</li>
	 * </ul>
	 * Allocation-free except for the final {@code substring}.
	 */
	static String extractHost(String uri) {
		int hostStart = 0;
		int schemeEnd = uri.indexOf("://");
		if (schemeEnd > 0) hostStart = schemeEnd + 3;

		// IPv6 literal: "[...]"
		if (hostStart < uri.length() && uri.charAt(hostStart) == '[') {
			int close = uri.indexOf(']', hostStart);
			if (close > 0) return uri.substring(hostStart + 1, close);
		}

		int end = uri.length();
		int pathSep = uri.indexOf('/', hostStart);
		if (pathSep >= 0) end = pathSep;
		int portSep = uri.indexOf(':', hostStart);
		if (portSep >= 0 && portSep < end) end = portSep;
		return uri.substring(hostStart, end);
	}

	public void start(HttpFiltersSource filters) throws Exception {
		try {
			internalProxy = DefaultHttpProxyServer.bootstrap()
					.withName("proxy-internal")
					.withPort(internalPort)
					.withConnectTimeout(connectTimeoutMs)
					.withIdleConnectionTimeout(idleTimeoutSeconds)
					.start();
		} catch (RuntimeException e) {
			throw new Exception(e.getCause() == null ? e : e.getCause());
		}
		localProxies = IntStream.of(localPorts)
				.mapToObj(port -> DefaultHttpProxyServer.bootstrap()
						.withName("proxy-" + port)
						.withPort(port)
						.withAllowLocalOnly(allowLocalOnly)
						.withConnectTimeout(connectTimeoutMs)
						.withIdleConnectionTimeout(idleTimeoutSeconds)
						.withChainProxyManager(chainedProxyManager)
						.withFiltersSource(filters)
						.start())
				.collect(toList());
	}

	public void stop() {
		if (localProxies != null) localProxies.forEach(SimpleProxyChain::abortQuietly);
		if (internalProxy != null) abortQuietly(internalProxy);
		localProxies = null;
		internalProxy = null;
	}

	private static void abortQuietly(HttpProxyServer server) {
		try {
			server.abort();
		} catch (Exception e) {
			log.debug("abort failed", e);
		}
	}

	@Override
	public String toString() {
		return String.format("SimpleProxyChain[upstream=%s:%d internal=%d local=%s noProxy=%s]",
				upstreamServer, upstreamPort, internalPort,
				Arrays.toString(localPorts), noProxyPattern);
	}
}
