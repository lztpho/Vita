# SPDX-License-Identifier: Apache-2.0
[CmdletBinding()]
param([ValidateSet('Debug','Release')][string]$Configuration = 'Debug')

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Android\Android Studio\jbr' }
$androidHome = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }

if (-not (Test-Path (Join-Path $javaHome 'bin\java.exe'))) { throw 'JDK 21 not found. Set JAVA_HOME.' }
if (-not (Test-Path $androidHome)) { throw 'Android SDK not found. Set ANDROID_HOME.' }

$env:JAVA_HOME = $javaHome
$env:ANDROID_HOME = $androidHome
Push-Location $repo
try {
    npm run android:sync
    if ($LASTEXITCODE -ne 0) { throw "Web build and Capacitor sync failed with exit code $LASTEXITCODE" }
    Push-Location (Join-Path $repo 'android')
    try {
        $task = if ($Configuration -eq 'Release') { ':app:assembleRelease' } else { ':app:assembleDebug' }
        & .\gradlew.bat $task '--console=plain'
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    } finally { Pop-Location }
} finally { Pop-Location }
