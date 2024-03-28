package com.baloise.proxy;

import static java.lang.String.format;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Version {
	private static Properties lazyGitProperties;
	private static final String GIT_COMMIT_ID_FULL = "git.commit.id.full";
	private static final Logger log = LoggerFactory.getLogger(Version.class);
	
	private Version() {}

	public static void openAbout() {
		String uri = format("https://github.com/baloise/proxy/blob/%s/README.md", gitCommitHash());
		try {
			Desktop.getDesktop().browse(new URI(uri));
		} catch (IOException | URISyntaxException e) {
			log.debug("Could not open "+uri, e);
		}
	}
	public static String gitCommitHash() {
		if(lazyGitProperties ==null) {
			lazyGitProperties = new Properties();
			String propFileName = "git.properties";
			try(InputStream gitProps = Proxy.class.getResourceAsStream(propFileName)){
				lazyGitProperties.load(gitProps);
			} catch (IOException e) {
				log.debug("could not read "+propFileName, e);
				lazyGitProperties.setProperty(GIT_COMMIT_ID_FULL, "__undefined__");
			}
		}		
		return lazyGitProperties.getProperty(GIT_COMMIT_ID_FULL);
	}

}
