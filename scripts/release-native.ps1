# Usage: .\scripts\release-native.ps1 3.21.0 "what changed"
# Bumps versionCode/versionName, builds the signed APK, publishes a GitHub Release.
# Phones running Heavyn't see the new release by themselves (Updater.kt), download it, and offer to install it.
param([Parameter(Mandatory)][string]$Version, [string]$Notes = 'Update')
$root = Split-Path $PSScriptRoot -Parent
$gradle = "$root\native\app\build.gradle.kts"
if (-not (Test-Path "$root\keystore\keystore.properties")) { throw 'keystore\keystore.properties is missing: a debug-signed APK cannot update installed phones' }
$g = Get-Content $gradle -Raw
$code = [int]([regex]::Match($g, 'versionCode = (\d+)').Groups[1].Value) + 1
$g = $g -replace 'versionCode = \d+', "versionCode = $code" -replace 'versionName = "[^"]+"', "versionName = `"$Version`""
Set-Content $gradle $g -NoNewline
& "$PSScriptRoot\build-native.ps1"
$apk = "$root\dist\heavynt.apk"
if (-not (Test-Path $apk)) { throw 'no apk' }
Set-Location $root
git add -A; git commit -m "v${Version}: $Notes"; git push
gh release create "v$Version" $apk --title "Heavyn't $Version" --notes $Notes
