# Crée l'exécutable Windows de H MOOD avec jpackage (fourni avec le JDK 21).
#   .\creer-executable.ps1                -> dist\H MOOD\H MOOD.exe (application autonome, Java embarqué)
#   .\creer-executable.ps1 -Installateur  -> dist\H MOOD-0.1.0.exe (installateur, nécessite WiX Toolset 3)
param([switch]$Installateur)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$jdk = Join-Path $env:USERPROFILE 'tools\jdk-21.0.12.1+1'
$mvnHome = Join-Path $env:USERPROFILE 'tools\apache-maven-3.9.9'
if (Test-Path $jdk) { $env:JAVA_HOME = $jdk; $env:PATH = "$jdk\bin;$env:PATH" }
if (Test-Path $mvnHome) { $env:PATH = "$mvnHome\bin;$env:PATH" }

Write-Host '1/3 Compilation et tests…'
mvn -q -B package
if ($LASTEXITCODE -ne 0) { throw 'La compilation ou les tests ont échoué.' }

Write-Host '2/3 Préparation…'
Remove-Item -Recurse -Force 'target\jpackage-input', 'dist' -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force 'target\jpackage-input' | Out-Null
Copy-Item 'target\hmood.jar' 'target\jpackage-input\'

$type = if ($Installateur) { 'exe' } else { 'app-image' }
$jpArgs = @(
    '--type', $type,
    '--name', 'H MOOD',
    '--app-version', '0.1.0',
    '--vendor', 'H MOOD',
    '--description', 'H MOOD - Ressens ton humeur. Vis ta musique.',
    '--input', 'target\jpackage-input',
    '--main-jar', 'hmood.jar',
    '--main-class', 'com.hmood.app.Launcher',
    '--add-modules', 'java.base,java.desktop,java.logging,java.naming,java.scripting,java.sql,java.xml,java.net.http,java.management,jdk.crypto.ec,jdk.unsupported,jdk.charsets,jdk.localedata,jdk.jsobject,jdk.xml.dom',
    '--java-options', '-Xmx512m',
    '--dest', 'dist'
)
if (Test-Path 'assets\hmood.ico') { $jpArgs += @('--icon', 'assets\hmood.ico') }
if ($Installateur) { $jpArgs += @('--win-menu', '--win-shortcut', '--win-dir-chooser') }

Write-Host "3/3 jpackage ($type)…"
& jpackage @jpArgs
if ($LASTEXITCODE -ne 0) { throw 'jpackage a échoué.' }

if ($Installateur) {
    Write-Host 'Installateur créé dans dist\'
} else {
    Write-Host 'Exécutable créé : dist\H MOOD\H MOOD.exe'
}
