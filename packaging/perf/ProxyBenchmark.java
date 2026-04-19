/*
 * Single-file benchmark that hammers an HTTP proxy with N requests across M threads
 * and reports throughput + latency percentiles. No external dependencies.
 *
 * Compile:   javac ProxyBenchmark.java
 * Run:       java ProxyBenchmark <proxyHost> <proxyPort> <targetURL> <threads> <requestsPerThread> [warmupRequestsPerThread]
 *
 * Example:   java ProxyBenchmark localhost 8888 http://example.com/ 8 50 10
 */

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyBenchmark {

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: java ProxyBenchmark <proxyHost> <proxyPort> <targetURL> <threads> <reqPerThread> [warmup=10]");
            System.exit(2);
        }
        String proxyHost = args[0];
        int    proxyPort = Integer.parseInt(args[1]);
        String targetUrl = args[2];
        int    threads   = Integer.parseInt(args[3]);
        int    perThread = Integer.parseInt(args[4]);
        int    warmup    = args.length > 5 ? Integer.parseInt(args[5]) : 10;

        // Disable HttpURLConnection keep-alive - we want honest connect+request timing each round.
        System.setProperty("http.keepAlive", "false");
        System.setProperty("sun.net.http.errorstream.enableBuffering", "false");

        System.out.printf(Locale.ROOT,
                "Proxy: %s:%d%nTarget: %s%nThreads: %d, req/thread: %d, warmup: %d%n%n",
                proxyHost, proxyPort, targetUrl, threads, perThread, warmup);

        // Warm-up (results discarded)
        if (warmup > 0) {
            run(proxyHost, proxyPort, targetUrl, threads, warmup, "warmup");
        }

        Result r = run(proxyHost, proxyPort, targetUrl, threads, perThread, "measure");
        r.print();

        // Machine-readable line for scripts
        System.out.printf(Locale.ROOT, "RESULT total=%d ok=%d fail=%d ms=%d rps=%.1f p50=%d p95=%d p99=%d min=%d max=%d%n",
                r.total, r.ok, r.fail, r.totalMs, r.rps(), r.p50, r.p95, r.p99, r.min, r.max);
    }

    static Result run(String proxyHost, int proxyPort, String targetUrl,
                      int threads, int perThread, String label) throws Exception {
        int total = threads * perThread;
        long[] latencies = new long[total];
        AtomicInteger idx = new AtomicInteger();
        AtomicInteger ok  = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go    = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP,
                new InetSocketAddress(proxyHost, proxyPort));
        URL url = new URL(targetUrl);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException ex) { return; }
                for (int i = 0; i < perThread; i++) {
                    long t0 = System.nanoTime();
                    try {
                        HttpURLConnection c = (HttpURLConnection) url.openConnection(proxy);
                        c.setConnectTimeout(10_000);
                        c.setReadTimeout(15_000);
                        c.setRequestProperty("Connection", "close");
                        int code = c.getResponseCode();
                        // drain body to complete the transaction
                        try (java.io.InputStream in = (code < 400 ? c.getInputStream() : c.getErrorStream())) {
                            if (in != null) {
                                byte[] buf = new byte[4096];
                                while (in.read(buf) > 0) { /* drain */ }
                            }
                        }
                        long dtMicros = (System.nanoTime() - t0) / 1000L;
                        latencies[idx.getAndIncrement()] = dtMicros;
                        if (code < 400) ok.incrementAndGet(); else fail.incrementAndGet();
                    } catch (Exception e) {
                        long dtMicros = (System.nanoTime() - t0) / 1000L;
                        latencies[idx.getAndIncrement()] = dtMicros;
                        fail.incrementAndGet();
                    }
                }
            });
        }

        ready.await();
        long start = System.nanoTime();
        go.countDown();

        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.MINUTES)) {
            pool.shutdownNow();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        long[] measured = Arrays.copyOf(latencies, idx.get());
        Arrays.sort(measured);

        Result r = new Result();
        r.label = label;
        r.total = total;
        r.ok = ok.get();
        r.fail = fail.get();
        r.totalMs = elapsedMs;
        if (measured.length > 0) {
            r.min = toMillis(measured[0]);
            r.max = toMillis(measured[measured.length - 1]);
            r.p50 = toMillis(measured[(int) (measured.length * 0.50)]);
            r.p95 = toMillis(measured[Math.min(measured.length - 1, (int) (measured.length * 0.95))]);
            r.p99 = toMillis(measured[Math.min(measured.length - 1, (int) (measured.length * 0.99))]);
        }
        return r;
    }

    private static long toMillis(long micros) { return micros / 1000L; }

    static class Result {
        String label = "";
        int total, ok, fail;
        long totalMs, min, max, p50, p95, p99;

        double rps() { return totalMs == 0 ? 0 : (ok * 1000.0) / totalMs; }

        void print() {
            System.out.printf(Locale.ROOT,
                    "--- %s ---%n" +
                    "requests:  %d  (ok=%d, fail=%d)%n" +
                    "duration:  %d ms%n" +
                    "throughput: %.1f req/s%n" +
                    "latency ms: min=%d  p50=%d  p95=%d  p99=%d  max=%d%n%n",
                    label, total, ok, fail, totalMs, rps(), min, p50, p95, p99, max);
        }
    }
}
