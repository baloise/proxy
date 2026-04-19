# Prepare a new release of the Baloise proxy into the external Chocolatey package.
#
# Steps:
#   1. mvn clean verify             (tests must pass)
#   2. build-native.ps1             (jlink + jpackage -> app-image)
#   3. Stage the app-image into the choco package's tools/ folder
#   4. Import the corporate zscaler CA into the embedded JRE cacerts
#
# After this finishes, run `choco pack` in the choco package directory to produce
# the .nupkg. (Choco itself is not automated here - you already have it set up.)
#
# Usage:
#   pwsh .\packaging\release-to-choco.ps1
#   pwsh .\packaging\release-to-choco.ps1 -SkipTests          # skip `mvn verify`
#   pwsh .\packaging\release-to-choco.ps1 -ChocoDir '...'     # override paths
#   pwsh .\packaging\release-to-choco.ps1 -ZscalerCert '...'

param(
    [string]$ChocoDir    = "$PSScriptRoot\..\..\chocolatey-internalizer-v2\custompackages\baloise-proxy2",
    [string]$ZscalerCert = "$PSScriptRoot\..\..\certbundler\certs\zscaler-ca.crt",
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSCommandPath)
Set-Location ..

if ($env:JAVA_HOME) {
    $keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"
} else {
    $keytool = "keytool"
}

function Step($n, $msg) { Write-Host "`n==> [$n] $msg" -ForegroundColor Cyan }

# --- 1. Tests -------------------------------------------------------------------
if ($SkipTests) {
    Step 1 "Skipping tests (--SkipTests)"
    & mvn -q clean package -DskipTests
} else {
    Step 1 "Running mvn clean verify (tests must be green)"
    & mvn clean verify
}
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

# --- 2. Native app-image --------------------------------------------------------
Step 2 "Building native Windows app-image (jlink + jpackage)"
& "$PSScriptRoot\build-native.ps1"
if ($LASTEXITCODE -ne 0) { throw "build-native.ps1 failed" }

$appImage = "target\native\output\Proxy"
if (-not (Test-Path $appImage)) { throw "app-image missing at $appImage" }

# --- 3. Stage into choco package ------------------------------------------------
Step 3 "Staging app-image into $ChocoDir\tools"
$ChocoDir = (Resolve-Path $ChocoDir).Path
$chocoTools = Join-Path $ChocoDir 'tools'
if (-not (Test-Path $chocoTools)) { throw "Choco tools dir missing: $chocoTools" }

# Remove every file/folder in tools/ except the install/uninstall PS scripts
Get-ChildItem $chocoTools -Exclude 'chocolateyInstall.ps1','chocolateyUninstall.ps1','chocolateyBeforeModify.ps1' |
    Remove-Item -Recurse -Force
Copy-Item -Recurse -Force "$appImage\*" $chocoTools

# --- 4. Import zscaler CA into the embedded trust store -------------------------
Step 4 "Importing zscaler CA into embedded JRE cacerts"
if (-not (Test-Path $ZscalerCert)) {
    throw "zscaler cert not found at $ZscalerCert - pass -ZscalerCert <path> or sync ../certbundler"
}
$cacerts = Join-Path $chocoTools 'runtime\lib\security\cacerts'
if (-not (Test-Path $cacerts)) { throw "cacerts missing at $cacerts - did jpackage run?" }

# keytool fails loudly if the alias already exists; delete first for idempotency.
& $keytool -delete -alias zscaler -keystore $cacerts -storepass changeit 2>&1 | Out-Null
& $keytool -import -trustcacerts -noprompt `
           -alias zscaler -keystore $cacerts -storepass changeit `
           -file $ZscalerCert
if ($LASTEXITCODE -ne 0) { throw "keytool import failed" }

# --- Summary --------------------------------------------------------------------
$size = [math]::Round((Get-ChildItem -Recurse $chocoTools | Measure-Object -Sum Length).Sum / 1MB, 1)
Write-Host "`nReady to pack." -ForegroundColor Green
Write-Host "Package dir: $ChocoDir"
Write-Host "Payload:     $size MB under tools/"
Write-Host ""
Write-Host "Next step:" -ForegroundColor Yellow
Write-Host "  cd `"$ChocoDir`""
Write-Host "  # bump <version> in baloise-proxy2.nuspec"
Write-Host "  choco pack .\baloise-proxy2.nuspec"
