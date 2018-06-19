package com.baloise.proxy;

import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSource;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;

public class FiltersSource407 extends HttpFiltersSourceAdapter implements HttpFiltersSource {
	
	private final Runnable onError;

	public FiltersSource407(Runnable onError) {
		this.onError = onError;
	}
	
	@Override
	public HttpFilters filterRequest(HttpRequest originalRequest) {

		return new HttpFiltersAdapter(originalRequest) {

			@Override
			public HttpObject proxyToClientResponse(HttpObject httpObject) {
				if (httpObject instanceof DefaultHttpResponse) {
					DefaultHttpResponse response = (DefaultHttpResponse) httpObject;
					int code = response.getStatus().code();
					if(code == 407) {
						onError.run();
					}
				}
				return httpObject;
			}

		};
	}
}
