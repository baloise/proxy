package com.baloise.proxy;

import static com.baloise.proxy.ImportTLSCert.tool;
import static com.baloise.proxy.Version.gitCommitHash;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.asList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baloise.proxy.config.Config.UpdateMode;
import com.baloise.proxy.ui.ProxyUI;

import common.OperatingSystem;
import common.Relaunch;

public class Update extends Thread {

	private final HTTPClient httpclient;
	long checkForUpdatesFrequencyInDays;
	UpdateMode updateMode;
	private final File lastUpdateCheck;
	private static final Logger log = LoggerFactory.getLogger(Update.class);
	private transient String branchName = OperatingSystem.CURRENT.name().toLowerCase().substring(0, 3)+"64";
	private final Path home;		
	private final String nextJar = "proxy_next.jar";
	private final String proxyJar = "proxy.jar";
	private final ProxyUI ui;

	public Update(HTTPClient httpclient,Path home, ProxyUI ui) {
		this.httpclient = httpclient;
		this.home = home;
		this.ui = ui;
		this.lastUpdateCheck = home.resolve("lastUpdateCheck").toFile();
		setName("Update Thread");
		setPriority(MIN_PRIORITY);
		setDaemon(true);
	}

	String getCurrentVersion() throws MalformedURLException, IOException, ParseException {
		HttpURLConnection con = httpclient.openConnection("https://api.github.com/repos/baloise/proxy/commits?per_page=1&sha="+branchName);
		try(InputStream in =  con.getInputStream()){
			 return parseSHA(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}
	
	static String parseSHA(String json) throws ParseException {
		for (String kvl : json.replaceAll("[\\'\\\"\\s{}\\[\\]]+", "").split(",")) {
			String[]  kv = kvl.split(":");
			if(kv[0].equals("sha")) return kv[1];
		}
		throw new ParseException("SHA not found",-1);
	}

	@Override
	public void run() {
		sleepy(3333);
		while(true) {			
			long aDayInMilliseconds = plusMinusTenPercent(24l *60l*60l*1000l);
			if(!UpdateMode.NONE.equals(updateMode) && 
					(currentTimeMillis() - getLastCheckedTimeStamp() > checkForUpdatesFrequencyInDays * aDayInMilliseconds)) {
				setLastCheckedTimeStamp();
				try {
					String latestVersion = getCurrentVersion();
					log.debug(format("latest version: %s, running version: %s ", latestVersion, gitCommitHash()));
					if(!latestVersion.equals(gitCommitHash())) {
						downLoadLatestVersion();
						applyLatestVersion();
					}
				} catch (IOException | ParseException e) {
					log.error("could not determine latest version", e);
				}
			}
			sleepy(aDayInMilliseconds);
		}
	}

	private static long plusMinusTenPercent(long input) {
		return (long) (input*0.9 + Math.random()* 0.2*input);
	}
	
	private void setLastCheckedTimeStamp() {
		if(!lastUpdateCheck.setLastModified(currentTimeMillis())) {
			if(lastUpdateCheck.exists() && !lastUpdateCheck.delete()) {
				log.error("Could not delete "+lastUpdateCheck.getAbsolutePath());						
			}
			try {
				lastUpdateCheck.createNewFile();
			} catch (IOException e) {
				log.error("Could not create "+lastUpdateCheck.getAbsolutePath(), e);
			}
		}
	}

	private void applyLatestVersion() {
		if(UpdateMode.IMMEDIATE.equals(updateMode) || ui.prompt("Apply update?", "A new version of the proxy will be installed on the next restart.\n Do you want to restart now?")) {
			startLatestVersionIfPresent();
		}
	}

	void startLatestVersionIfPresent() {
		if(home.resolve(nextJar).toFile().exists()) {
			try {
				log.info("restarting with new version");
				final String javaBin = tool("java");
				List<String> cmd = new ArrayList<>(asList(
						javaBin, 
						"-jar",
						Relaunch.writeJar().getAbsolutePath(),
						proxyJar,
						nextJar,
						"update.log",
						javaBin,
						"-jar",
						proxyJar
						));
				new ProcessBuilder(cmd)
				.directory(home.toFile())
				.inheritIO()
				.start();
				System.exit(0);
			} catch (Exception e) {
				log.error("Could not apply lastest proxy version", e);
			}
		}
	}

	private void downLoadLatestVersion() throws MalformedURLException, IOException {
		String url = format("https://jitpack.io/com/github/baloise/proxy/%s-SNAPSHOT/proxy-%s-SNAPSHOT.jar",branchName,branchName);
		HttpURLConnection con = httpclient.openConnection(url);
		try(InputStream in =  con.getInputStream()){
			try(OutputStream out = new FileOutputStream(home.resolve(nextJar).toFile())){
				in.transferTo(out);
			}
		} catch (IOException e) {
			log.error("Could not download update from "+url, e);
		}
	}

	private long getLastCheckedTimeStamp() {
		return lastUpdateCheck.lastModified();
	}

	private void sleepy(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException expected) {}
	}

}
