# proxy
An minimal authenticating HTTP(S) forward proxy based on https://github.com/adamfisk/LittleProxy. You can easily add sniffing / rewriting if needed. In short: Fiddler in Java 

![](https://jitpack.io/v/baloise/proxy.svg)

# Installation

## With Powershell on Windows

### Download the binary. (works also for updates)

```
md "$env:USERPROFILE\.proxy2" -ea 0 | Out-Null; iwr https://jitpack.io/com/github/baloise/proxy/win64-SNAPSHOT/proxy-win64-SNAPSHOT.jar -OutFile $env:USERPROFILE\.proxy2\proxy.jar

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
