# CLAUDE.md — Non-negotiable guardrails for the Baloise Proxy

This project forwards **every** outgoing request of its user's workstation. It
must be fast, reliable, and not break silently. These rules exist because a
broken or slow proxy cripples developer productivity — there is no "it's just a
tool, we can tolerate regressions."

## The three pillars (never drop one)

### 1. Regression protection
- **Every code change runs `mvn clean verify` and all tests must be green.**
- **Do not "optimize" or "simplify" code without a matching test.** If the behaviour was untested and is now load-bearing, write the test *before* you touch the code.
- Integration test `SimpleProxyChainIntegrationTest` is the safety net for the request path. If you change `SimpleProxyChain`, `FiltersSource407`, `Config`, `ProxyUI`, or their wiring, you **must** extend this test.
- Flaky tests are bugs. Fix the root cause, don't reduce assertions.

### 2. Performance
- The proxy sits on the hot path of every HTTP request. Keep per-request overhead minimal:
  - No new allocations in the `ChainedProxyManager.lookupChainedProxies` / `ChainedProxyAdapter.filterRequest` path beyond what Netty forces.
  - Pre-compile regexes, pre-resolve addresses, cache headers.
  - No blocking I/O in a Netty event loop.
  - Logging in hot paths must be guarded with `log.isDebugEnabled()`.
- **Before claiming a change is neutral or positive for performance, run `packaging/perf/bench-compare.ps1`** — it benchmarks this project against a baseline (typically the currently deployed 8888 instance) and prints a NEW-vs-OLD delta. Keep p50/p95 within noise or improve them.

### 3. Quality & test coverage
- Aim for **every branch in the request/config/password hot path** to have at least one test.
- Tests that currently guard hot paths:
  | Area | Test |
  |---|---|
  | URI → host extraction | `SimpleProxyChainHostTest` |
  | 407 burst → once-only callback | `FiltersSource407Test` |
  | Config parsing / equality | `ConfigTest` |
  | Config file change → debounced callback | `FileWatcherTest` |
  | Password XOR roundtrip | `CryptoTest` |
  | End-to-end routing + auth + concurrency | `SimpleProxyChainIntegrationTest` |
- Use JUnit Jupiter (JUnit 5). Don't re-introduce JUnit 4.
- A PR that reduces coverage is a rejected PR.

## Release readiness checklist

Before a change is "done":

1. `mvn clean verify` → green. *No exceptions.*
2. `pwsh .\packaging\perf\bench-compare.ps1` → NEW ≥ OLD on throughput, p50, p95 (or within ±5% noise).
3. `pwsh .\packaging\build-native.ps1` → `target/native/output/Proxy/Proxy.exe` builds and is self-contained.
4. README / DEVELOPMENT.md mention any new user-visible switch.

## Guardrail: things not to add back

- Auto-update subsystem (removed deliberately; deployment happens via Chocolatey).
- Console/SWT UI (AWT with headless degradation covers every case).
- JVM / env-variable auto-configuration (brittle, was the source of most bugs).
- JUnit 4 or Mockito with reflection magic that bypasses the real wire behaviour of LittleProxy.

## Coding style reminders

- No field-initializer that can throw (e.g. AWT `PopupMenu` throws `HeadlessException`). Use lazy construction.
- Default to `volatile` over synchronized for single-field publication (see `cachedAuthHeaderValue`).
- `Thread.yield()` is not a scheduling primitive; prefer `Thread.sleep`, `wait/notify`, or a `BlockingQueue`.
- Don't swallow `InterruptedException` without re-setting the interrupt flag.

## Pointers

- `SimpleProxyChain` — request routing + auth injection (hot path).
- `FiltersSource407` — one-shot auth-failure callback.
- `Config` + `FileWatcher` — live-reload; keep effective-config-based `equals()` so cosmetic edits don't restart the proxy.
- `Proxy` — orchestration, tray UI wiring, restart-in-place.
- `packaging/perf/` — benchmark harness (do not delete; this is how we detect regressions).
