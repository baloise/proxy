# proxy

A minimal authenticating HTTP(S) forward proxy based on [LittleProxy](https://github.com/adamfisk/LittleProxy).
Runs locally and forwards requests to a corporate upstream proxy, handling basic-auth and a `no_proxy` regex.

## Build

```
mvn clean verify
```

- Unit + integration tests run under JUnit Jupiter.
- Shaded runnable JAR: `target/dist/proxy-<version>.jar`.

## Run (JAR)

```
java -jar target/dist/proxy-<version>.jar
```

The first start writes a default `~/.proxy/proxy.properties` and opens it for editing.

## Native Windows exe (embedded JRE)

```
pwsh .\packaging\build-native.ps1
```

Produces `target/native/output/Proxy/` containing `Proxy.exe` + `runtime/` + `app/`.
The whole folder is self-contained — copy it anywhere and run `Proxy.exe`.

## Release into the Chocolatey package

The Chocolatey package lives in a separate repository at
`..\chocolatey-internalizer-v2\custompackages\baloise-proxy2\`.

**Full release flow = one script + one choco command:**

```powershell
pwsh .\packaging\release-to-choco.ps1       # tests + build + stage + cert import
cd ..\chocolatey-internalizer-v2\custompackages\baloise-proxy2
# bump <version> in baloise-proxy2.nuspec
choco pack .\baloise-proxy2.nuspec
```

`release-to-choco.ps1` does:
1. `mvn clean verify` — tests must be green (pass `-SkipTests` to skip).
2. `packaging\build-native.ps1` — jlink + jpackage → `target\native\output\Proxy\`.
3. Copies the app-image into the choco package's `tools\`.
4. Imports `zscaler-ca.crt` from `..\certbundler\certs\` into the embedded JRE cacerts.

If your repo layout differs, override paths:

```
pwsh .\packaging\release-to-choco.ps1 -ChocoDir '...' -ZscalerCert '...'
```

## Configuration

| Key | Default | Description |
|-----|---------|-------------|
| `SimpleProxyChain.port` | `8888` | Local listener port(s), comma-separated |
| `SimpleProxyChain.internalPort` | `8889` | Loopback bypass port for `no_proxy` hosts |
| `SimpleProxyChain.upstreamServer` | auto | Upstream proxy host (detected from `HTTP(S)_PROXY`) |
| `SimpleProxyChain.upstreamPort` | auto | Upstream proxy port |
| `SimpleProxyChain.useAuth` | `false` | Send `Proxy-Authorization: Basic …` upstream |
| `SimpleProxyChain.noproxyHostsRegEx` | `--!!!--` | Hosts matching this regex bypass the upstream |
| `allowLocalOnly` | `true` | Accept only localhost clients |
| `connectTimeoutMs` | `10000` | Upstream connect timeout |
| `idleTimeoutSeconds` | `70` | Idle connection timeout |
| `testURL` | `https://example.com/` | URL hit by the tray "Test" entry |

The file is watched; edits are picked up without restart when they change anything effective.

## Password

Set the upstream credentials via the tray **Password** entry, or headless:

```
java -jar proxy.jar -password=secret
```

The password is XOR-obfuscated and stored in the user-scoped Java `Preferences` node
`com/baloise/proxy/password` (or `com/baloise/windows` if already present).
