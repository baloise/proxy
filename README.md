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

### Install

```

$proxyFolder = "$env:USERPROFILE\.proxy" 
md $proxyFolder -ea 0 | Out-Null
$jreFolder = "$proxyFolder\jre21"

function unzip ($src, $dst){
    $shell=new-object -com shell.application
    $items = $shell.NameSpace($src).items()
    if($items.Count -ne 1) {
        Write-Output ("ABORTING: Expected exactly one folder in jre zip, but found "+$items.Count)
        exit
    }
    $folderName = ($items| Select-Object -first 1).Name
    Expand-Archive -LiteralPath $src -DestinationPath $dst
    return $folderName
}


if (!(Test-Path -Path $jreFolder)) {
    Write-Output "$jreFolder not found ... downloading"
 	$JRE_ZIP="$proxyFolder\jre21.zip"
	iwr "https://api.foojay.io/disco/v3.0/directuris?distro=temurin&javafx_bundled=false&libc_type=c_std_lib&archive_type=zip&operating_system=windows&package_type=jre&version=21&architecture=x64&latest=available"  -OutFile $JRE_ZIP
	$jreFolderName = unzip $JRE_ZIP $proxyFolder
    Remove-Item $JRE_ZIP
    Move-Item -Path "$proxyFolder\$jreFolderName" -Destination $jreFolder
}
echo  "jre OK"

iwr https://jitpack.io/com/github/baloise/proxy/win64-SNAPSHOT/proxy-win64-SNAPSHOT.jar -OutFile $env:USERPROFILE\.proxy\proxy.jar
echo  "proxy OK"

"powershell -Command Start-Process '$jreFolder\bin\javaw.exe' '-jar $env:USERPROFILE\.proxy\proxy.jar' -WindowStyle Hidden" | Out-File -Encoding oem -FilePath "$proxyFolder\proxy.bat"

"""C:\Users\b028178\.proxy\jre21\bin\java.exe"" -jar ""C:\Users\b028178\.proxy\proxy.jar"" `r`npause" | Out-File -Encoding oem -FilePath "$proxyFolder\proxy_debug.bat"

"powershell -Command Start-Process '$proxyFolder\proxy.bat' -WindowStyle Hidden" | Out-File -Encoding oem -FilePath "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup\proxy.bat"

echo  "start scripts OK"
echo "to start the proxy run the following command:"
echo "Start-Process '$env:USERPROFILE\.proxy\proxy.bat' -WindowStyle Hidden"

```
You can look up the current proxy version @ https://jitpack.io/com/github/baloise/proxy/proxy/-SNAPSHOT/maven-metadata.xml

### Run
```
Start-Process "$env:USERPROFILE\.proxy\proxy.bat" -WindowStyle Hidden
```
