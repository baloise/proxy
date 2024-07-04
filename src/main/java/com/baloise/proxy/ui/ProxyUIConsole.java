package com.baloise.proxy.ui;

import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyUIConsole implements ProxyUI {
	Logger log = LoggerFactory.getLogger(ProxyUI.class);
	Map<String, ActionListener> menu = new HashMap<>();
	Set<String> labels = new TreeSet<>();
	final int MenuPrefixLength = 1;
	int actionId;
	{
		new Thread() {
			{setDaemon(false);}
			public void run() {
				while (true) {
					String line = readLine();
					final ActionListener actionListener = menu.get(line.toLowerCase());
					if(actionListener!= null) {
						actionListener.actionPerformed(new ActionEvent(menu, ++actionId, line));
					} else {
						labels.forEach(ProxyUIConsole.this::printMenuEntry);
					}
				}
			}
		}.start();
	}
	
	void printMenuEntry(String label) {
		printf("Menu: [%s]%s\n", label.substring(0, MenuPrefixLength),label.substring(MenuPrefixLength));
	}
	
	private void printf(String format, Object ...args) {
		System.out.printf(format, args);
		System.out.flush();
	}

	@Override
	public ProxyUI withMenuEntry(String label, ActionListener actionListener) {
		if("Restart".equals(label)) {
			// TODO this is a hack
			log.warn("Suppressing restart in console mode - not yet implemented");
			return this;
		}
		labels.add(label);
		printMenuEntry(label);
		if(menu.put(label.toLowerCase().substring(0, MenuPrefixLength), actionListener)!=null) {
			throw new IllegalArgumentException("Second menu entry with the same starting letter: "+label);
		}
		return this;
	}

	@Override
	public void show() {
	}

	@Override
	public void displayMessage(String caption, String text, MessageType messageType) {
		printf("%s : %s\n%s\n", messageType, caption, text);
	}

	@Override
	public void showHTLM(boolean success, String title, String html) {
		printf("%s : %s\n%s\n", success?MessageType.INFO:MessageType.ERROR, title, html);
	}

	@Override
	public boolean prompt(String caption, String text) {
		String input = readLine("%s\n%s\nYes/No? [No]\n", caption, text);
		return input!=null && input.toLowerCase().contains("y");
	}

	@Override
	public Entry<PasswordDialogResult, String> showPasswordDialog() {
		PasswordDialogResult result = PasswordDialogResult.ofValue(readLine("1 - Set password\n2 - Remove password\n3 - Cancel\n1/2/3? [3]"));
		return new AbstractMap.SimpleEntry<>(result, 
					result == PasswordDialogResult.SET 
					? new String(readPassword("Proxy password (will not be shown as you type):\n"))
				    :""
				); 
	}
	
	private String readLine(){
		return readLine("");
	}
	
	private String readLine(String format, Object... args) {
	    if (System.console() != null) {
	        return System.console().readLine(format, args);
	    }
	    printf(format, args);
	    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
	    try {
			return reader.readLine();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private char[] readPassword(String format, Object... args) {
	    if (System.console() != null)
	        return System.console().readPassword(format, args);
	    return readLine(format, args).toCharArray();
	}
	
}
