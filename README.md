# proxy
An minimal authenticating HTTP(S) forward proxy based on https://github.com/adamfisk/LittleProxy. You can easily add sniffing / rewriting if needed. In short: Fiddler in Java 

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/1698a76ba3a34ec58987c232546ba005)](https://app.codacy.com/app/baloise/proxy?utm_source=github.com&utm_medium=referral&utm_content=baloise/proxy&utm_campaign=Badge_Grade_Dashboard)
[![](https://jitpack.io/v/baloise/proxy.svg)](https://jitpack.io/com/github/baloise/proxy/master-SNAPSHOT/proxy-master-SNAPSHOT.jar)

# Installation

## on Windows

### Download the binary. (works also for updates)

```
if not exist %userprofile%\.proxy mkdir %userprofile%\.proxy
powershell -Command "$proxy = [System.Net.WebRequest]::GetSystemWebProxy();$proxy.Credentials = [System.Net.CredentialCache]::DefaultCredentials;$wc = new-object system.net.WebClient;$wc.proxy = $proxy;$wc.DownloadFile('https://jitpack.io/com/github/baloise/proxy/master-SNAPSHOT/proxy-master-SNAPSHOT.jar', '%USERPROFILE%/.proxy/proxy.jar');"
```
You can look up the current proxy version @ https://jitpack.io/com/github/baloise/proxy/proxy/-SNAPSHOT/maven-metadata.xml

### Create start up item
```
echo powershell -Command Start-Process 'javaw.exe' '-jar "%userprofile%\.proxy\proxy.jar"' -NoNewWindow > "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup\proxy.bat"
```

### Run
```
powershell -Command Start-Process 'javaw.exe' '-jar "%userprofile%\.proxy\proxy.jar"' -NoNewWindow
```
