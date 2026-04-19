/*
 * Fair end-to-end benchmark comparator.
 *
 * Spawns an in-process HTTP origin, starts the NEW proxy as a SEPARATE JVM
 * subprocess, and benchmarks OLD (external, on <oldPort>) vs NEW (subprocess,
 * on <newPort>). Uses persistent HTTP/1.1 keep-alive connections to avoid
 * Windows TIME_WAIT exhaustion (on Windows TCP sockets linger 1-4 minutes in
 * TIME_WAIT; without keep-alive a few thousand requests burn all ephemeral
 * ports and the second round fails wholesale).
 *
 * The benchmark runs R rounds alternating OLD/NEW/OLD/NEW/... and reports the
 * median of each side.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

public class BenchmarkCompare {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: BenchmarkCompare <jar> [oldPort=8888] [newPort=18888] [threads=8] [reqPerRound=200] [rounds=5]");
            System.exit(2);
        }
        String jarPath = args[0];
        int oldPort = args.length > 1 ? Integer.parseInt(args[1]) : 8888;
        int newPort = args.length > 2 ? Integer.parseInt(args[2]) : 18888;
        int threads = args.length > 3 ? Integer.parseInt(args[3]) : 8;
        int reqPerRound = args.length > 4 ? Integer.parseInt(args[4]) : 300;
        int rounds = args.length > 5 ? Integer.parseInt(args[5]) : 7;
        // Enough per-thread volume to push both JVMs through C2 (~10k req threshold).
        int warmupReq = 1500;

        // Use HTTP keep-alive to exercise a realistic proxy workload AND avoid
        // burning an ephemeral port per request (Windows TIME_WAIT trap).
        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.maxConnections", String.valueOf(threads * 2));

        if (!isListening("127.0.0.1", oldPort)) {
            System.err.printf("OLD proxy not listening on :%d - aborting.%n", oldPort);
            System.exit(1);
        }

        int originPort = freePort();
        HttpServer origin = startOrigin(originPort);
        String targetUrl = "http://127.0.0.1:" + originPort + "/bench";
        System.out.printf(Locale.ROOT, "Origin:  %s%n", targetUrl);
        System.out.printf(Locale.ROOT, "Clients: %d threads x %d req per round x %d rounds (keep-alive, warmup %d)%n%n",
                threads, reqPerRound, rounds, warmupReq);

        int internalPort = freePort();
        Path benchHome = Files.createTempDirectory("proxy-bench-home-");
        Path proxyDir = benchHome.resolve(".proxy");
        Files.createDirectories(proxyDir);
        writeProps(proxyDir.resolve("proxy.properties").toFile(), newPort, internalPort);

        Process newProxy = startNewProxyProcess(jarPath, benchHome);
        try {
            if (!waitForListening("127.0.0.1", newPort, 20_000)) {
                dumpLog(benchHome);
                newProxy.destroyForcibly();
                throw new IllegalStateException("NEW proxy did not start on :" + newPort + " within 20s");
            }
            System.out.printf("NEW proxy subprocess pid=%d listening on :%d%n%n", newProxy.pid(), newPort);

            // Shared warmup to JIT-compile both sides.
            bench("warm-old", oldPort, targetUrl, threads, warmupReq);
            bench("warm-new", newPort, targetUrl, threads, warmupReq);

            List<Result> oldResults = new ArrayList<>();
            List<Result> newResults = new ArrayList<>();
            for (int r = 1; r <= rounds; r++) {
                Result o = bench("OLD-r" + r, oldPort, targetUrl, threads, reqPerRound);
                Result n = bench("NEW-r" + r, newPort, targetUrl, threads, reqPerRound);
                printCompact(o);
                printCompact(n);
                oldResults.add(o);
                newResults.add(n);
            }

            // Steady-state view: drop the first 2 rounds (cold start / JIT tier-up).
            int skip = Math.min(2, rounds - 1);
            Result oldMed = median(oldResults.subList(skip, oldResults.size()));
            Result newMed = median(newResults.subList(skip, newResults.size()));

            System.out.println("\n=== MEDIAN across steady-state rounds (" + (rounds - skip) + " of " + rounds + ") ===");
            System.out.printf(Locale.ROOT, "OLD: rps=%.1f  p50=%d  p95=%d  p99=%d  fail=%d%n",
                    oldMed.rps, oldMed.p50, oldMed.p95, oldMed.p99, oldMed.fail);
            System.out.printf(Locale.ROOT, "NEW: rps=%.1f  p50=%d  p95=%d  p99=%d  fail=%d%n",
                    newMed.rps, newMed.p50, newMed.p95, newMed.p99, newMed.fail);

            System.out.println("\n=== DELTA (NEW vs OLD) ===");
            System.out.printf(Locale.ROOT, "throughput: %.1f -> %.1f req/s  (%s%.1f%%)  %s%n",
                    oldMed.rps, newMed.rps, sign(newMed.rps - oldMed.rps), pct(oldMed.rps, newMed.rps),
                    newMed.rps >= oldMed.rps ? "(NEW wins)" : "(OLD wins)");
            System.out.printf(Locale.ROOT, "p50:        %d -> %d ms  (%s%.1f%%)  %s%n",
                    oldMed.p50, newMed.p50, sign(newMed.p50 - oldMed.p50), pct(oldMed.p50, newMed.p50),
                    newMed.p50 <= oldMed.p50 ? "(NEW wins)" : "(OLD wins)");
            System.out.printf(Locale.ROOT, "p95:        %d -> %d ms  (%s%.1f%%)  %s%n",
                    oldMed.p95, newMed.p95, sign(newMed.p95 - oldMed.p95), pct(oldMed.p95, newMed.p95),
                    newMed.p95 <= oldMed.p95 ? "(NEW wins)" : "(OLD wins)");
            System.out.printf(Locale.ROOT, "errors: old=%d  new=%d%n", oldMed.fail, newMed.fail);
        } finally {
            newProxy.destroy();
            newProxy.waitFor(3, TimeUnit.SECONDS);
            if (newProxy.isAlive()) newProxy.destroyForcibly();
            origin.stop(0);
            try { deleteRecursive(benchHome.toFile()); } catch (Exception ignored) { }
        }
    }

    private static String sign(double v) { return v >= 0 ? "+" : ""; }
    private static String sign(long v)   { return v >= 0 ? "+" : ""; }
    private static double pct(double o, double n) { return o == 0 ? 0 : (n - o) * 100.0 / o; }
    private static double pct(long o, long n) { return pct((double) o, (double) n); }

    private static void dumpLog(Path benchHome) throws IOException {
        File log = benchHome.resolve("subprocess.log").toFile();
        if (!log.exists()) return;
        System.err.println("--- NEW proxy subprocess output ---");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Files.newInputStream(log.toPath()), StandardCharsets.UTF_8))) {
            String ln; while ((ln = r.readLine()) != null) System.err.println(ln);
        }
        System.err.println("--- end log ---");
    }

    private static Process startNewProxyProcess(String jar, Path fakeHome) throws IOException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        File log = fakeHome.resolve("subprocess.log").toFile();
        ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "-Duser.home=" + fakeHome.toString(),
                "-Djava.awt.headless=true",
                "-Dfile.encoding=UTF-8",
                "-jar", jar
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(log);
        return pb.start();
    }

    private static void writeProps(File f, int port, int internalPort) throws IOException {
        try (FileWriter w = new FileWriter(f)) {
            w.write("SimpleProxyChain.port=" + port + "\n");
            w.write("SimpleProxyChain.internalPort=" + internalPort + "\n");
            w.write("SimpleProxyChain.upstreamServer=127.0.0.1\n");
            w.write("SimpleProxyChain.upstreamPort=1\n");
            w.write("SimpleProxyChain.useAuth=false\n");
            w.write("SimpleProxyChain.noproxyHostsRegEx=.*\n");
            w.write("allowLocalOnly=true\n");
            w.write("connectTimeoutMs=10000\n");
            w.write("idleTimeoutSeconds=70\n");
        }
    }

    private static boolean waitForListening(String host, int port, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (isListening(host, port)) return true;
            Thread.sleep(200);
        }
        return false;
    }

    private static boolean isListening(String host, int port) {
        try (Socket s = new Socket()) { s.connect(new InetSocketAddress(host, port), 400); return true; }
        catch (IOException e) { return false; }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
    }

    private static HttpServer startOrigin(int port) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        byte[] body = new byte[64];
        Arrays.fill(body, (byte) 'a');
        s.createContext("/", ex -> {
            ex.getResponseHeaders().set("Content-Type", "text/plain");
            // don't set Connection: close -> keep-alive
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });
        s.setExecutor(Executors.newFixedThreadPool(16));
        s.start();
        return s;
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) for (File c : f.listFiles()) deleteRecursive(c);
        f.delete();
    }

    private static void printCompact(Result r) {
        System.out.printf(Locale.ROOT, "  %-10s rps=%6.1f  p50=%3d  p95=%3d  p99=%3d  fail=%d%n",
                r.label, r.rps, r.p50, r.p95, r.p99, r.fail);
    }

    private static Result median(List<Result> xs) {
        List<Result> sorted = new ArrayList<>(xs);
        sorted.sort((a, b) -> Double.compare(a.rps, b.rps));
        return sorted.get(sorted.size() / 2);
    }

    // --- benchmark core ---------------------------------------------------------

    static Result bench(String label, int proxyPort, String targetUrl, int threads, int perThread)
            throws Exception {
        int total = threads * perThread;
        long[] lat = new long[total];
        AtomicInteger idx = new AtomicInteger();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP,
                new InetSocketAddress("127.0.0.1", proxyPort));
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
                        // keep-alive is default; do NOT set Connection: close
                        int code = c.getResponseCode();
                        drain(code < 400 ? c.getInputStream() : c.getErrorStream());
                        long dt = (System.nanoTime() - t0) / 1000L;
                        lat[idx.getAndIncrement()] = dt;
                        if (code < 400) ok.incrementAndGet(); else fail.incrementAndGet();
                    } catch (Exception e) {
                        long dt = (System.nanoTime() - t0) / 1000L;
                        int id = idx.getAndIncrement();
                        if (id < lat.length) lat[id] = dt;
                        fail.incrementAndGet();
                    }
                }
            });
        }
        ready.await();
        long start = System.nanoTime();
        go.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.MINUTES)) pool.shutdownNow();
        long elapsed = (System.nanoTime() - start) / 1_000_000L;

        long[] sorted = Arrays.copyOf(lat, idx.get());
        Arrays.sort(sorted);

        Result r = new Result();
        r.label = label;
        r.total = total;
        r.ok = ok.get();
        r.fail = fail.get();
        r.totalMs = elapsed;
        r.rps = elapsed == 0 ? 0 : (r.ok * 1000.0) / elapsed;
        if (sorted.length > 0) {
            r.min = ms(sorted[0]);
            r.max = ms(sorted[sorted.length - 1]);
            r.p50 = ms(sorted[(int) (sorted.length * 0.50)]);
            r.p95 = ms(sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.95))]);
            r.p99 = ms(sorted[Math.min(sorted.length - 1, (int) (sorted.length * 0.99))]);
        }
        return r;
    }

    private static void drain(InputStream in) throws IOException {
        if (in == null) return;
        byte[] buf = new byte[4096];
        try (InputStream s = in) {
            while (s.read(buf) > 0) { /* drain */ }
        }
    }

    private static long ms(long micros) { return micros / 1000L; }

    static class Result {
        String label = "";
        int total, ok, fail;
        long totalMs, min, max, p50, p95, p99;
        double rps;
    }
}
