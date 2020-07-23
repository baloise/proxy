package com.baloise.proxy.config;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.function.Consumer;

public class Config {
	public final Path PROXY_HOME = Paths.get(System.getProperty("user.home"), ".proxy");
	public final Path PROXY_PROPERTIES = PROXY_HOME.resolve("proxy.properties");

	public Config() {
		PROXY_HOME.toFile().mkdirs();
		if (!PROXY_PROPERTIES.toFile().exists()) {
			try (FileOutputStream out = new FileOutputStream(PROXY_PROPERTIES.toFile())) {
				createDefault().store(out, null);
				openProperties();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void onPropertyChange(Consumer<File> onChange) {
		new FileWatcher(PROXY_PROPERTIES.toFile(), onChange).start();
	}

	public void openProperties() {
		open(PROXY_PROPERTIES.toFile());
	}
	
	public Properties load() {
		Properties p = new Properties();
		try (InputStream in = new FileInputStream(PROXY_PROPERTIES.toFile())) {
			p.load(in);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return p;
	}

	private void open(File file) {
		try {
			Desktop.getDesktop().open(file);
		} catch (IOException e) {
			try {
				Desktop.getDesktop().open(file.getParentFile());
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	private Properties createDefault() {
		Properties p = new Properties();
		p.setProperty("SimpleProxyChain.upstreamServer", "proxy");
		p.setProperty("SimpleProxyChain.upstreamPort", "8888");
		p.setProperty("SimpleProxyChain.port", "8888");
		p.setProperty("SimpleProxyChain.internalPort", "8889");
		p.setProperty("SimpleProxyChain.noproxyHostsRegEx", "--!!!--");
		return p;
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		Config config = new Config();
		config.load().list(System.out);
	}

}
