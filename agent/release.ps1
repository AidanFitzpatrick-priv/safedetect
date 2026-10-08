# Builds, tests and publishes a GitHub release with SafeDetect-<version>.zip (jar + install.bat + NOTICE).
# Usage: powershell -ExecutionPolicy Bypass -File agent\release.ps1 [-Notes "text"] [-Draft]
#   The version comes from Implementation-Version in agent\MANIFEST.MF; the tag is v<version>.
#   Uses the GitHub login git already has (Git Credential Manager). Commit and push first.
param([string]$Notes = "", [switch]$Draft)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here
$repo = "AidanFitzpatrick-priv/safedetect"

$version = (Select-String -Path (Join-Path $here "MANIFEST.MF") -Pattern "^Implementation-Version:\s*(\S+)").Matches[0].Groups[1].Value
if (-not $version) { throw "No Implementation-Version in MANIFEST.MF" }
$tag = "v$version"

Push-Location $root
try {
    if (git status --porcelain) { throw "Commit your changes first; the release is built from the pushed commit." }
    git fetch -q origin
    $head = (git rev-parse HEAD).Trim()
    $branch = (git branch --show-current).Trim()
    if ($head -ne (git rev-parse "origin/$branch").Trim()) { throw "Push $branch first." }
} finally {
    Pop-Location
}

& (Join-Path $here "build.ps1") -Test
if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw "build failed" }

$out = Join-Path $here "out"
$stage = Join-Path $out "SafeDetect-$tag"
$zip = Join-Path $out "SafeDetect-$tag.zip"
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item (Join-Path $out "safedetect-agent.jar") $stage
Copy-Item (Join-Path $root "release\install.bat") $stage
Copy-Item (Join-Path $root "NOTICE.txt") $stage
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $zip
Write-Host "Packed $zip"

$cred = "protocol=https`nhost=github.com`n`n" | git credential fill
$token = ($cred | Where-Object { $_ -like "password=*" }) -replace "^password=", ""
if (-not $token) { throw "No GitHub login stored in git. Run a git push once to sign in." }
$headers = @{ Authorization = "Bearer $token"; Accept = "application/vnd.github+json"; "User-Agent" = "SafeDetect-release" }
$api = "https://api.github.com/repos/$repo"

if (-not $Notes) {
    $Notes = @"
**Install:** download ``SafeDetect-$tag.zip``, extract it, run ``install.bat`` and follow the last step it prints (paste one JVM argument into Lunar Client, 1.8.9).

For Lunar Client 1.8.9 only. Fully close Lunar before installing or updating.
"@
}

$release = $null
try {
    $release = Invoke-RestMethod -Headers $headers -Uri "$api/releases/tags/$tag"
    Write-Host "Release $tag already exists; replacing its files."
} catch {
    $body = @{ tag_name = $tag; target_commitish = $head; name = "SafeDetect $tag"; body = $Notes; draft = [bool]$Draft } | ConvertTo-Json
    $release = Invoke-RestMethod -Method Post -Headers $headers -Uri "$api/releases" -Body $body -ContentType "application/json"
    Write-Host "Created release $tag"
}

foreach ($file in @($zip, (Join-Path $out "safedetect-agent.jar"))) {
    $name = Split-Path -Leaf $file
    foreach ($asset in $release.assets) {
        if ($asset.name -eq $name) {
            Invoke-RestMethod -Method Delete -Headers $headers -Uri "$api/releases/assets/$($asset.id)" | Out-Null
        }
    }
    $upload = "https://uploads.github.com/repos/$repo/releases/$($release.id)/assets?name=$name"
    Invoke-RestMethod -Method Post -Headers $headers -Uri $upload -InFile $file -ContentType "application/octet-stream" | Out-Null
    Write-Host "Uploaded $name"
}
Write-Host "Done: $($release.html_url)"
