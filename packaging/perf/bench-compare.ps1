# Benchmark OLD proxy (assumed running on localhost:8888) vs a freshly-built NEW
# proxy started in-process on 18888. Writes numbers to console.
#
# Usage: pwsh .\packaging\perf\bench-compare.ps1 [-OldPort 8888] [-NewPort 18888] [-Threads 8] [-Requests 200]

param(
    [int]$OldPort  = 8888,
    [int]$NewPort  = 18888,
    [int]$Threads  = 8,
    [int]$Requests = 200
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSCommandPath)
Set-Location ..\..

if ($env:JAVA_HOME) {
    $java  = Join-Path $env:JAVA_HOME "bin\java.exe"
    $javac = Join-Path $env:JAVA_HOME "bin\javac.exe"
} else {
    $java = "java"; $javac = "javac"
}

# Ensure shaded jar is built.
$jar = Get-ChildItem -Path target\dist -Filter "proxy-*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) {
    Write-Host "Shaded jar missing, running mvn package..."
    & mvn -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
    $jar = Get-ChildItem -Path target\dist -Filter "proxy-*.jar" | Select-Object -First 1
}
Write-Host "Using jar: $($jar.Name)"

$benchOut = "target\bench-classes"
if (Test-Path $benchOut) { Remove-Item -Recurse -Force $benchOut }
New-Item -ItemType Directory -Path $benchOut | Out-Null

& $javac -cp $jar.FullName -d $benchOut packaging\perf\BenchmarkCompare.java
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$cp = "$benchOut;$($jar.FullName)"
Write-Host ""
& $java -cp $cp BenchmarkCompare $jar.FullName $OldPort $NewPort $Threads $Requests
