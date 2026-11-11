# Copies the reviewed Java sources from D:\ehospital-review into this project, verifying SHA-256 hashes
# before and after copying, then runs the tests. Nothing is copied if any staged file does not match.
param([string]$Review = 'D:\ehospital-review', [switch]$SkipTests)
$ErrorActionPreference = 'Stop'
$project  = Split-Path -Parent $MyInvocation.MyCommand.Path
$manifest = Join-Path $project 'java-sync-manifest.txt'
if (-not (Test-Path $manifest)) { throw "Manifest not found: $manifest" }
$entries = Get-Content $manifest | Where-Object { $_ -and -not $_.StartsWith('#') } | ForEach-Object {
  $p = $_ -split '\s\|\s'; [pscustomobject]@{ Hash = $p[0].Trim().ToLower(); Source = Join-Path $Review $p[1].Trim(); Target = Join-Path $project $p[2].Trim() }
}
Write-Host "Checking $($entries.Count) staged Java files in $Review ..."
$bad = @()
foreach ($e in $entries) {
  if (-not (Test-Path $e.Source)) { $bad += "missing: $($e.Source)"; continue }
  $h = (Get-FileHash -Algorithm SHA256 $e.Source).Hash.ToLower()
  if ($h -ne $e.Hash) { $bad += "hash mismatch: $($e.Source)" }
}
if ($bad.Count) { $bad | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }; throw "Staged files do not match the manifest. Nothing was copied." }
$changed = 0
foreach ($e in $entries) {
  $dir = Split-Path -Parent $e.Target; if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
  $before = if (Test-Path $e.Target) { (Get-FileHash -Algorithm SHA256 $e.Target).Hash.ToLower() } else { '' }
  if ($before -ne $e.Hash) { Copy-Item -LiteralPath $e.Source -Destination $e.Target -Force; $changed++ ; Write-Host "  updated  $($e.Target.Substring($project.Length + 1))" }
}
foreach ($e in $entries) {
  if ((Get-FileHash -Algorithm SHA256 $e.Target).Hash.ToLower() -ne $e.Hash) { throw "Verification failed after copy: $($e.Target)" }
}
Write-Host "All $($entries.Count) Java files in the original project match the manifest ($changed updated)." -ForegroundColor Green
if (-not $SkipTests) {
  Push-Location $project
  try { & .\mvnw.cmd '-Dmaven.repo.local=.cache/m2' -o test; if ($LASTEXITCODE -ne 0) { throw "Tests failed (exit $LASTEXITCODE)." } }
  finally { Pop-Location }
  Write-Host "Tests passed. Start the app with: mvnw.cmd -Dmaven.repo.local=.cache/m2 -o spring-boot:run" -ForegroundColor Green
}
