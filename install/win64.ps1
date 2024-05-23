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

"""$env:USERPROFILE\.proxy\jre21\bin\java.exe"" -jar ""$env:USERPROFILE\.proxy\proxy.jar"" `r`npause" | Out-File -Encoding oem -FilePath "$proxyFolder\proxy_debug.bat"

"powershell -Command Start-Process '$proxyFolder\proxy.bat' -WindowStyle Hidden" | Out-File -Encoding oem -FilePath "$env:APPDATA\Microsoft\Windows\Start Menu\Programs\Startup\proxy.bat"

echo  "start scripts OK"
echo "to start the proxy run the following command:"
echo "Start-Process '$env:USERPROFILE\.proxy\proxy.bat' -WindowStyle Hidden"