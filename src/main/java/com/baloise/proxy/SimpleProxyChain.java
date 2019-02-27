package com.baloise.proxy;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;

import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.Queue;
import java.util.regex.Pattern;

import org.littleshoot.proxy.ChainedProxy;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.ChainedProxyManager;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import common.BasicAuth;
import common.User;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;

public class SimpleProxyChain {

	private final int UPSTREAM_PORT;
	private final String UPSTREAM_SERVER;
	private final int INTERNAL_PORT;
	public final int PORT;
	private final Pattern NO_PROXY_HOSTS_REGEX;
	private final ChainedProxyManager chainedProxyManager;
	private HttpProxyServer internalProxy;
	private HttpProxyServer upstreamProxy;
	Logger log = LoggerFactory.getLogger(SimpleProxyChain.class);


	public SimpleProxyChain(Properties props) {
		this(
				props.getProperty("SimpleProxyChain.upstreamServer", "proxy"),
				parseInt(props.getProperty("SimpleProxyChain.upstreamPort", "8888")) ,
				parseInt(props.getProperty("SimpleProxyChain.port", "8888")),
				parseInt(props.getProperty("SimpleProxyChain.internalPort", "8889")),
				props.getProperty("SimpleProxyChain.noproxyHostsRegEx", "--!!!--"),
				parseBoolean(props.getProperty("SimpleProxyChain.useAuth", "true"))
			);
	}
	
	public SimpleProxyChain(String upstreamServer, int upstreamPort, int port, int internalPort, String noproxyHostsRegEx, boolean useAuth) {
		this.UPSTREAM_PORT = upstreamPort;
		this.UPSTREAM_SERVER = upstreamServer;
		this.INTERNAL_PORT = internalPort;
		this.PORT = port;
		this.NO_PROXY_HOSTS_REGEX = Pattern.compile(noproxyHostsRegEx);
		
		log.info(this.toString());
		log.info("user: " + User.get());
		
		ChainedProxyAdapter webproxy = new ChainedProxyAdapter() {
			@Override
			public InetSocketAddress getChainedProxyAddress() {
				return new InetSocketAddress(UPSTREAM_SERVER, UPSTREAM_PORT);
			}
			
			String authParam = BasicAuth.get();
			
			@Override
			public void filterRequest(HttpObject httpObject) {
				if (httpObject instanceof HttpRequest && useAuth) {
					HttpRequest httpRequest = (HttpRequest) httpObject;
					httpRequest.headers().add("Proxy-Authorization", "Basic " + authParam);
				}
			}
			
		};
		ChainedProxyAdapter no_proxy = new ChainedProxyAdapter() {
			@Override
			public InetSocketAddress getChainedProxyAddress() {
				return new InetSocketAddress("localhost", INTERNAL_PORT);
			}
		};
		chainedProxyManager = new ChainedProxyManager() {
			
			@Override
			public void lookupChainedProxies(HttpRequest httpRequest, Queue<ChainedProxy> chainedProxies) {
				if (noProxy(getHost(httpRequest))) {
					if(log.isDebugEnabled())
						log.debug("calling "+ httpRequest.getUri() + " without proxy");
					chainedProxies.add(no_proxy);
				} else {
					if(log.isDebugEnabled())
						log.debug("calling "+ httpRequest.getUri() + " trough upstream proxy");
					chainedProxies.add(webproxy);
				}
			}
			
			private boolean noProxy(String host) {
				return NO_PROXY_HOSTS_REGEX.matcher(host).matches();
			}
			
		};
	}

	private  String getHost(HttpRequest httpRequest) {
		String[] tokens = httpRequest.getUri().split("/+", 3);
		String host = tokens.length == 1 ? tokens[0] : tokens[1];
		tokens = host.split(":", 2);
		host = tokens[0];
		return host;
	}

	public void start(HttpFiltersSource filters) {
		internalProxy = DefaultHttpProxyServer.bootstrap().withPort(INTERNAL_PORT).start();
		upstreamProxy = DefaultHttpProxyServer.bootstrap().withPort(PORT).withChainProxyManager(chainedProxyManager)
				.withFiltersSource(filters)
				.start();
	}
	
	public  void stop() {
		internalProxy.stop();
		upstreamProxy.stop();
	}

	@Override
	public String toString() {
		return "SimpleProxyChain [UPSTREAM_PORT=" + UPSTREAM_PORT + ", UPSTREAM_SERVER=" + UPSTREAM_SERVER
				+ ", INTERNAL_PORT=" + INTERNAL_PORT + ", PORT=" + PORT + ", NO_PROXY_HOSTS_REGEX="
				+ NO_PROXY_HOSTS_REGEX + "]";
	}
	
	

}