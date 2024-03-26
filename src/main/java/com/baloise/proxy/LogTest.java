package com.baloise.proxy;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

import org.slf4j.LoggerFactory;

public class LogTest {

	public static void main(String[] args) throws SecurityException, FileNotFoundException, IOException {
		try(InputStream logProps = Proxy.class.getResourceAsStream("logging.properties")){
			LogManager.getLogManager().readConfiguration(logProps);
		}
		
		
		org.slf4j.Logger log = LoggerFactory.getLogger(LogTest.class);
		log.info("SLF");
		log.error("bla", new IOException("Blub"));
	}

}
