# Builds safedetect-agent.jar with JDK 8 (no Forge, no Gradle).
# Usage: powershell -ExecutionPolicy Bypass -File agent\build.ps1 [-Deploy]
param([switch]$Deploy)

$ErrorActionPreference = "Stop"
$jdk = "C:\Users\Aidan\.jdks\temurin8\jdk8u504-b01"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $here "out"
$classes = Join-Path $out "classes"
$jar = Join-Path $out "safedetect-agent.jar"

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $classes | Out-Null

$sources = Get-ChildItem (Join-Path $here "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& "$jdk\bin\javac.exe" -source 8 -target 8 -encoding UTF-8 -Xlint:all -Xlint:-options -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

& "$jdk\bin\jar.exe" cfm $jar (Join-Path $here "MANIFEST.MF") -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
Write-Host "Built $jar"

if ($Deploy) {
    $target = "C:\Users\Aidan\AppData\Roaming\.minecraft\safedetect"
    New-Item -ItemType Directory -Force $target | Out-Null
    Copy-Item $jar (Join-Path $target "safedetect-agent.jar") -Force
    Write-Host "Copied to $target\safedetect-agent.jar"
}
