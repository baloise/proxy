package com.baloise.proxy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class FileWatcherTest {

	@Test
	void notifiesOnChange(@TempDir Path tmp) throws Exception {
		File file = tmp.resolve("cfg.properties").toFile();
		Files.writeString(file.toPath(), "initial=1");

		CountDownLatch seen = new CountDownLatch(1);
		FileWatcher watcher = new FileWatcher(file, f -> seen.countDown());
		watcher.start();
		try {
			// WatchService needs to be registered before we write. Give it a moment.
			Thread.sleep(200);
			Files.writeString(file.toPath(), "changed=2");
			assertTrue(seen.await(5, TimeUnit.SECONDS), "onChange must fire within 5s");
		} finally {
			watcher.interrupt();
		}
	}

	@Test
	void doesNotFireForEmptyFile(@TempDir Path tmp) throws Exception {
		File file = tmp.resolve("cfg.properties").toFile();
		Files.writeString(file.toPath(), "initial=1");

		AtomicInteger fires = new AtomicInteger();
		FileWatcher watcher = new FileWatcher(file, f -> fires.incrementAndGet());
		watcher.start();
		try {
			Thread.sleep(200);
			// Truncate to 0 bytes (simulates atomic save midway).
			Files.writeString(file.toPath(), "");
			Thread.sleep(600);
			assertEquals(0, fires.get(), "must not fire for truncated/empty file");
		} finally {
			watcher.interrupt();
		}
	}

	@Test
	void debouncesBurstsOfEvents(@TempDir Path tmp) throws Exception {
		File file = tmp.resolve("cfg.properties").toFile();
		Files.writeString(file.toPath(), "v=0");

		AtomicInteger fires = new AtomicInteger();
		FileWatcher watcher = new FileWatcher(file, f -> fires.incrementAndGet());
		watcher.start();
		try {
			Thread.sleep(200);
			for (int i = 1; i <= 5; i++) {
				Files.writeString(file.toPath(), "v=" + i);
			}
			// Allow the watcher to drain and the debounce window to expire.
			Thread.sleep(800);
			int count = fires.get();
			// With a 200ms debounce, 5 rapid writes should collapse into far fewer events
			// (often 1-2). Anything < 5 proves debouncing kicked in; typically we see 1.
			assertTrue(count >= 1, "expected at least one onChange, got " + count);
			assertTrue(count < 5, "expected debounce to collapse events, got " + count);
		} finally {
			watcher.interrupt();
		}
	}
}
