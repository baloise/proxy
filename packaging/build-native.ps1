# Builds a Windows app-image (Proxy.exe + embedded JRE) using jlink + jpackage.
# Run after `mvn clean package`. Needs JDK 21+ via JAVA_HOME (or jlink/jpackage on PATH).

param(
    [string]$Version = "2.0.0",
    [string]$Vendor  = "Baloise"
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSCommandPath)
Set-Location ..

if ($env:JAVA_HOME) {
    $jlink    = Join-Path $env:JAVA_HOME "bin\jlink.exe"
    $jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
    $java     = Join-Path $env:JAVA_HOME "bin\java.exe"
} else {
    $jlink = "jlink"; $jpackage = "jpackage"; $java = "java"
}

Write-Host "JDK: $(& $java --version | Select-Object -First 1)" -ForegroundColor Cyan

$jar = Get-ChildItem -Path target\dist -Filter "proxy-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) { throw "No proxy-*.jar in target/dist. Run 'mvn clean package' first." }

$buildDir   = "target\native"
$runtimeDir = "$buildDir\runtime"
$inputDir   = "$buildDir\input"
$outputDir  = "$buildDir\output"

if (Test-Path $buildDir) { Remove-Item -Recurse -Force $buildDir }
New-Item -ItemType Directory -Path $inputDir | Out-Null

Copy-Item $jar.FullName "$inputDir\proxy.jar"

# Modules derived from: jdeps --print-module-deps (plus jdk.crypto.ec for TLS elliptic curves,
# jdk.zipfs/jdk.jfr as safety margin, java.security.jgss + java.sql pulled in by Guava).
$modules = "java.base,java.compiler,java.desktop,java.logging,java.management,java.naming,java.prefs,java.security.jgss,java.sql,jdk.crypto.ec,jdk.jfr,jdk.unsupported,jdk.zipfs"

Write-Host "jlink -> minimal runtime..." -ForegroundColor Cyan
& $jlink --output $runtimeDir `
         --add-modules $modules `
         --strip-debug --no-header-files --no-man-pages `
         --compress=2
if ($LASTEXITCODE -ne 0) { throw "jlink failed" }

Write-Host "jpackage -> app-image..." -ForegroundColor Cyan
$jpArgs = @(
    "--type", "app-image",
    "--name", "Proxy",
    "--app-version", $Version,
    "--vendor", $Vendor,
    "--input", $inputDir,
    "--main-jar", "proxy.jar",
    "--main-class", "com.baloise.proxy.Proxy",
    "--runtime-image", $runtimeDir,
    "--dest", $outputDir,
    "--java-options", "-Dfile.encoding=UTF-8",
    "--java-options", "-Djava.awt.headless=false"
)
$ico = "src\main\resources\com\baloise\proxy\ui\proxy_icon.ico"
if (Test-Path $ico) { $jpArgs += @("--icon", $ico) }

& $jpackage @jpArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

$proxyExe = Join-Path $outputDir "Proxy\Proxy.exe"
$size     = [math]::Round((Get-ChildItem -Recurse "$outputDir\Proxy" | Measure-Object -Sum Length).Sum / 1MB, 1)
Write-Host ""
Write-Host "App-image ready: $proxyExe ($size MB total)" -ForegroundColor Green
Write-Host "Copy the entire 'Proxy' folder (exe + runtime/ + app/) to any Windows host and run Proxy.exe."
