package com.baloise.proxy;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

public interface HTTPClient {

	HttpURLConnection openConnection(String url) throws MalformedURLException, IOException;

}