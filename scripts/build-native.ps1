# Builds the signed native (Kotlin / Jetpack Compose) release APK -> dist/heavynt.apk
$ErrorActionPreference = 'Continue'
$root = Split-Path $PSScriptRoot -Parent
Set-Location "$root\native"
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot' }
$env:ANDROID_HOME = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Android' }
cmd /c ".\gradlew.bat testDebugUnitTest assembleRelease"
if ($LASTEXITCODE) { throw 'gradle failed' }
New-Item -ItemType Directory -Force "$root\dist" | Out-Null
Copy-Item app\build\outputs\apk\release\app-release.apk "$root\dist\heavynt.apk" -Force
Write-Host "APK ready: dist\heavynt.apk"
