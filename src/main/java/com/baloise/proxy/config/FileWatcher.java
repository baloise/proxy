package com.baloise.proxy.config;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches a single file for modifications and invokes onChange when it changes.
 * Debounces rapid successive events (editors often fire two ENTRY_MODIFY events per save).
 */
public class FileWatcher extends Thread {

	private static final long DEBOUNCE_MS = 200L;

	private final File file;
	private final Consumer<File> onChange;
	private final Logger log = LoggerFactory.getLogger(FileWatcher.class);

	public FileWatcher(File file, Consumer<File> onChange) {
		this.file = file;
		this.onChange = onChange;
		setName("proxy-config-watcher");
		setDaemon(true);
	}

	@Override
	public void run() {
		Path dir = file.toPath().toAbsolutePath().getParent();
		try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
			dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
			long lastFired = 0L;
			while (!Thread.currentThread().isInterrupted()) {
				WatchKey key = watcher.take();
				boolean relevant = false;
				for (WatchEvent<?> event : key.pollEvents()) {
					if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;
					Path changed = (Path) event.context();
					if (changed != null && file.getName().equals(changed.toString())) {
						relevant = true;
					}
				}
				if (!key.reset()) break;

				long now = System.currentTimeMillis();
				if (relevant && file.length() > 0 && now - lastFired > DEBOUNCE_MS) {
					lastFired = now;
					try {
						onChange.accept(file);
					} catch (RuntimeException ex) {
						log.error("onChange handler failed", ex);
					}
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.error("FileWatcher failed", e);
		}
	}
}
