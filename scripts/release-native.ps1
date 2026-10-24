# Usage: .\scripts\release-native.ps1 3.0.1 "what changed"   (bump versionCode/versionName in native/app/build.gradle.kts first)
param([Parameter(Mandatory)][string]$Version, [string]$Notes = 'Update')
$root = Split-Path $PSScriptRoot -Parent
& "$PSScriptRoot\build-native.ps1"
$apk = "$root\dist\heavynt.apk"
if (-not (Test-Path $apk)) { throw 'no apk' }
Set-Location $root
gh release create "v$Version" $apk --title "Heavyn't $Version" --notes $Notes