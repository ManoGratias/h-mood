# Lance H MOOD (compile le JAR s'il n'existe pas encore).
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$jdk = Join-Path $env:USERPROFILE 'tools\jdk-21.0.12.1+1'
$mvnHome = Join-Path $env:USERPROFILE 'tools\apache-maven-3.9.9'
if (Test-Path $jdk) { $env:JAVA_HOME = $jdk; $env:PATH = "$jdk\bin;$env:PATH" }
if (Test-Path $mvnHome) { $env:PATH = "$mvnHome\bin;$env:PATH" }

if (-not (Test-Path 'target\hmood.jar')) {
    Write-Host 'Compilation de H MOOD…'
    mvn -q -B package -DskipTests
}
& java -jar target\hmood.jar
