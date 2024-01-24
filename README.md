# proxy
An minimal authenticating HTTP(S) forward proxy based on https://github.com/adamfisk/LittleProxy. You can easily add sniffing / rewriting if needed. In short: Fiddler in Java 

![](https://jitpack.io/v/baloise/proxy.svg)

# Prerequisites

Java 11+ is on the path. To check run `java --version`. The expected output is something like
```
openjdk 11.0.12 2021-07-20
OpenJDK Runtime Environment Microsoft-25199 (build 11.0.12+7)
OpenJDK 64-Bit Server VM Microsoft-25199 (build 11.0.12+7, mixed mode)
```
with at leat 11.x.y
 
# Installation

## With Powershell on Windows

### Download the binary. (works also for updates)

```
md "$env:USERPROFILE\.proxy" -ea 0 | Out-Null; iwr https://jitpack.io/com/github/baloise/proxy/win64-SNAPSHOT/proxy-win64-SNAPSHOT.jar -OutFile $env:USERPROFILE\.proxy\proxy.jar

```
You can look up the current proxy version @ https://jitpack.io/com/github/baloise/proxy/proxy/-SNAPSHOT/maven-metadata.xml

### Create start up item
```
"powershell -Command Start-Process 'javaw.exe' '-jar $env:USERPROFILE\.proxy\proxy.jar' -NoNewWindow" | Out-File -Encoding oem -FilePath "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup\proxy.bat"
```

### Run
```
Start-Process 'javaw.exe' "-jar $env:USERPROFILE\.proxy\proxy.jar" -NoNewWindow
```
