# Builds a signed release APK -> dist/workout-tracker.apk
$ErrorActionPreference = 'Continue'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot' }
$env:ANDROID_HOME = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Android' }
node build.mjs; if ($LASTEXITCODE) { throw 'bundle failed' }
npx cap sync android; if ($LASTEXITCODE) { throw 'cap sync failed' }
Push-Location android
cmd /c ".\gradlew.bat assembleRelease"
if ($LASTEXITCODE) { throw 'gradle failed' }
Pop-Location
New-Item -ItemType Directory -Force dist | Out-Null
Copy-Item android\app\build\outputs\apk\release\app-release.apk dist\workout-tracker.apk -Force
Write-Host "APK ready: dist\workout-tracker.apk"
