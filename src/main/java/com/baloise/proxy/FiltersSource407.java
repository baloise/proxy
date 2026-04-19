package com.baloise.proxy;

import java.util.concurrent.atomic.AtomicBoolean;

import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;

import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Invokes {@code onAuthFailure} at most once when the upstream returns HTTP 407.
 * After firing, subsequent requests take a zero-allocation fast path using the
 * library's shared no-op filter.
 */
public class FiltersSource407 extends HttpFiltersSourceAdapter {

	/** Library singleton. Cheaper than constructing a filter per request. */
	private static final HttpFilters NOOP = HttpFiltersAdapter.NOOP_FILTER;

	private final Runnable onAuthFailure;
	private final AtomicBoolean fired = new AtomicBoolean(false);

	public FiltersSource407(Runnable onAuthFailure) {
		this.onAuthFailure = onAuthFailure;
	}

	@Override
	public HttpFilters filterRequest(HttpRequest originalRequest) {
		if (fired.get()) return NOOP;
		return new HttpFiltersAdapter(originalRequest) {
			@Override
			public HttpObject proxyToClientResponse(HttpObject httpObject) {
				if (httpObject instanceof HttpResponse
						&& ((HttpResponse) httpObject).getStatus().code() == 407
						&& fired.compareAndSet(false, true)) {
					onAuthFailure.run();
				}
				return httpObject;
			}
		};
	}
}
