package com.baloise.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

class FiltersSource407Test {

	@Test
	void firesOnceOn407() {
		AtomicInteger fires = new AtomicInteger();
		FiltersSource407 src = new FiltersSource407(fires::incrementAndGet);

		// Burst of 407 responses: only the first should fire the callback.
		for (int i = 0; i < 50; i++) {
			src.filterRequest(newRequest())
				.proxyToClientResponse(new DefaultHttpResponse(HttpVersion.HTTP_1_1,
						HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED));
		}
		assertEquals(1, fires.get(), "onAuthFailure must only fire once for a 407 burst");
	}

	@Test
	void doesNotFireOnOtherStatuses() {
		AtomicInteger fires = new AtomicInteger();
		FiltersSource407 src = new FiltersSource407(fires::incrementAndGet);

		for (HttpResponseStatus status : new HttpResponseStatus[]{
				HttpResponseStatus.OK,
				HttpResponseStatus.NOT_FOUND,
				HttpResponseStatus.INTERNAL_SERVER_ERROR,
				HttpResponseStatus.UNAUTHORIZED  // 401 not 407
		}) {
			src.filterRequest(newRequest())
				.proxyToClientResponse(new DefaultHttpResponse(HttpVersion.HTTP_1_1, status));
		}
		assertEquals(0, fires.get());
	}

	private static DefaultHttpRequest newRequest() {
		return new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://example.com/");
	}
}
