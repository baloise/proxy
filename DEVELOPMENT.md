# Development

## Build

JDK 21 (LTS). Runs on Windows, Linux, macOS.

```
mvn clean verify
```

- Compiles all sources.
- Runs JUnit Jupiter unit + integration tests (end-to-end proxy test included).
- Produces a shaded runnable JAR at `target/dist/proxy-<version>.jar`.

## Native Windows exe (embedded JRE)

```
pwsh .\packaging\build-native.ps1
```

Produces a self-contained app-image under `target/native/output/Proxy/`:

- `Proxy.exe` — the tray-app launcher.
- `runtime/` — jlink-trimmed JRE (~55 MB).
- `app/` — `proxy.jar` + classpath metadata.

Ship the whole `Proxy/` folder together; it has no external dependencies.

### jlink module set

Derived from `jdeps --print-module-deps` against the shaded jar, plus TLS-EC support:

```
java.base,java.compiler,java.desktop,java.logging,java.management,java.naming,
java.prefs,java.security.jgss,java.sql,jdk.crypto.ec,jdk.jfr,jdk.unsupported,jdk.zipfs
```

## Chocolatey package

The Chocolatey package lives in a separate repository:
`..\chocolatey-internalizer-v2\custompackages\baloise-proxy2\`

Full release flow — steps 1-4 automated in `packaging\release-to-choco.ps1`:

```powershell
pwsh .\packaging\release-to-choco.ps1
cd ..\chocolatey-internalizer-v2\custompackages\baloise-proxy2
# bump <version> in baloise-proxy2.nuspec
choco pack .\baloise-proxy2.nuspec
```

The script runs `mvn verify`, builds the native app-image, stages it into the
choco package's `tools\`, and imports the corporate zscaler CA into the embedded
JRE cacerts. Override `-ChocoDir` / `-ZscalerCert` / `-SkipTests` if needed.

## Icons

PNGs in `src/main/resources/com/baloise/proxy/ui/` are committed; SVG originals are kept
for reference. No build-time regeneration. If you replace an icon, commit both.
