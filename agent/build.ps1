# Builds safedetect-agent.jar with JDK 8 (no Forge, no Gradle).
# Usage: powershell -ExecutionPolicy Bypass -File agent\build.ps1 [-Jdk <path>] [-Test] [-Deploy]
#   -Jdk     JDK 8 home. Defaults to $env:JAVA_HOME, then the newest JDK 8 under ~\.jdks.
#   -Test    Compiles and runs the unit checks and the mock-game harness; fails the build on any failure.
#   -Deploy  Copies the jar to %APPDATA%\.minecraft\safedetect.
param([string]$Jdk = "", [switch]$Test, [switch]$Deploy)

$ErrorActionPreference = "Stop"

function Find-Jdk {
    if ($Jdk) { return $Jdk }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) { return $env:JAVA_HOME }
    $home8 = Get-ChildItem (Join-Path $env:USERPROFILE ".jdks") -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match "8u|jdk8|temurin8" -and (Test-Path (Join-Path $_.FullName "bin\javac.exe")) } |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($home8) { return $home8.FullName }
    throw "No JDK found. Pass -Jdk <path> or set JAVA_HOME to a JDK 8."
}

$jdk = Find-Jdk
$javac = Join-Path $jdk "bin\javac.exe"
$java = Join-Path $jdk "bin\java.exe"
$jarTool = Join-Path $jdk "bin\jar.exe"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$out = Join-Path $here "out"
$classes = Join-Path $out "classes"
$jar = Join-Path $out "safedetect-agent.jar"

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $classes | Out-Null

function Sources([string]$dir) {
    return Get-ChildItem $dir -Recurse -Filter *.java | ForEach-Object { $_.FullName }
}

& $javac -source 8 -target 8 -encoding UTF-8 -Xlint:all -Xlint:-options -d $classes (Sources (Join-Path $here "src"))
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

& $jarTool cfm $jar (Join-Path $here "MANIFEST.MF") -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
Write-Host "Built $jar"

if ($Test) {
    $testOut = Join-Path $out "test"
    $mock = Join-Path $testOut "mock"
    $harness = Join-Path $testOut "harness"
    $unit = Join-Path $testOut "unit"
    $data = Join-Path $testOut "data"
    foreach ($dir in @($mock, $harness, $unit, (Join-Path $data "config"))) {
        New-Item -ItemType Directory -Force $dir | Out-Null
    }

    & $javac -source 8 -target 8 -encoding UTF-8 -nowarn -d $mock (Sources (Join-Path $here "test\mock"))
    if ($LASTEXITCODE -ne 0) { throw "mock javac failed" }
    & $javac -source 8 -target 8 -encoding UTF-8 -nowarn -d $harness (Sources (Join-Path $here "test\harness"))
    if ($LASTEXITCODE -ne 0) { throw "harness javac failed" }
    & $javac -source 8 -target 8 -encoding UTF-8 -nowarn -cp $classes -d $unit (Sources (Join-Path $here "test\unit"))
    if ($LASTEXITCODE -ne 0) { throw "unit javac failed" }

    & $java -cp "$classes;$unit" com.safedetect.agent.UnitChecks
    if ($LASTEXITCODE -ne 0) { throw "unit checks failed" }

    $seed = @'
{
  "players": [
    {"uuid": "b30a4714-cbba-48e0-b335-8d04cbdc1392", "name": "noahhh727_alt", "flags": ["SN"], "details": {}, "counts": {}, "times": 1, "first": 0, "last": 0},
    {"uuid": "6c1a2b3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", "name": "Wemzy_on_top", "flags": ["FK"], "details": {"FK": "9 stars, 3.0 FKDR"}, "counts": {}, "times": 1, "first": 0, "last": 0}
  ]
}
'@
    [System.IO.File]::WriteAllText((Join-Path $data "config\safedetect-flags.json"), $seed)

    & $java "-javaagent:$jar" "-Dsafedetect.noUpdate=true" -cp $harness sdtest.Launch $mock $data
    if ($LASTEXITCODE -ne 0) { throw "harness checks failed" }
}

if ($Deploy) {
    $target = Join-Path $env:APPDATA ".minecraft\safedetect"
    New-Item -ItemType Directory -Force $target | Out-Null
    Copy-Item $jar (Join-Path $target "safedetect-agent.jar") -Force
    Write-Host "Copied to $target\safedetect-agent.jar"
}
