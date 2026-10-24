# Publishes dist/workout-tracker.apk as a GitHub Release. Usage: .\scripts\release.ps1 1.0.1 "what changed"
param([Parameter(Mandatory)][string]$Version, [string]$Notes = 'Update')
$ErrorActionPreference = 'Continue'
Set-Location (Split-Path $PSScriptRoot -Parent)
& "$PSScriptRoot\build-apk.ps1"
gh release create "v$Version" dist\workout-tracker.apk --title "Workout Tracker $Version" --notes $Notes
