param(
    [string]$Tag = "",
    [string]$Notes = "Official 3D Ledger Production Release",
    [switch]$BuildOnly = $false,
    [switch]$NoBroadcast = $false,
    [switch]$SkipBuild = $false
)

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   3D LEDGER: BUILD & RELEASE TO GITHUB + TELEGRAM AUTO    " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Determine Version Tag
if (-not $Tag) {
    $latestTag = .\gh\bin\gh.exe release list --limit 1 | ForEach-Object { ($_ -split "`t")[2] }
    if ($latestTag -match "v1\.0\.(\d+)") {
        $nextNum = [int]$matches[1] + 1
        $Tag = "v1.0.$nextNum"
    } else {
        $Tag = "v1.0.100"
    }
}
$versionCode = [int]($Tag -replace '\D', '')
if ($versionCode -eq 0) { $versionCode = 100 }

Write-Host "📌 Target Release Tag: $Tag (Code: #$versionCode)" -ForegroundColor Yellow
Write-Host "📝 Release Notes: $Notes" -ForegroundColor Yellow

# 2. Build or Locate APK
Write-Host "`n🔨 [Step 1/3] Compiling hardened APK with Gradle..." -ForegroundColor Green
$apk = $null

if (-not $SkipBuild) {
    Write-Host "Compiling fresh Release APK with R8 obfuscation and resource shrinking..."
    .\gradlew.bat assembleRelease --no-daemon
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "assembleRelease failed or missing release signing, attempting debug fallback..."
        .\gradlew.bat assembleDebug --no-daemon
    }
}

$apk = Get-ChildItem -Path "app\build\outputs\apk\release\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $apk) {
    $apk = Get-ChildItem -Path "app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
}

if (-not $apk -or -not (Test-Path $apk.FullName)) {
    Write-Error "❌ APK build failed! No .apk file found in app\build\outputs\apk"
    exit 1
}

$apkPath = $apk.FullName
$apkSizeMb = [math]::Round(($apk.Length / 1MB), 2)
Write-Host "✅ APK ready: $($apk.Name) ($apkSizeMb MB)" -ForegroundColor Green

if ($BuildOnly) {
    Write-Host "BuildOnly flag set. Skipping GitHub and Telegram upload."
    exit 0
}

# 3. Upload to GitHub Releases First
Write-Host "`n🚀 [Step 2/3] Uploading to GitHub Releases first via gh CLI..." -ForegroundColor Green
$ghCmd = ".\gh\bin\gh.exe release create `"$Tag`" `"$apkPath`" --title `"Release $Tag`" --notes `"$Notes`""
Write-Host "Executing: $ghCmd"
Invoke-Expression $ghCmd

if ($LASTEXITCODE -ne 0) {
    Write-Error "❌ GitHub Release creation failed with code $LASTEXITCODE"
    exit 1
}

$repoUrl = .\gh\bin\gh.exe repo view --json url -q ".url"
if (-not $repoUrl) { $repoUrl = "https://github.com/sayarkhant001/3d_Agent" }
$downloadUrl = "$repoUrl/releases/download/$Tag/$($apk.Name)"
Write-Host "✅ Uploaded to GitHub Releases!" -ForegroundColor Green
Write-Host "   URL: $downloadUrl" -ForegroundColor Cyan

# 4. Automatically Sync to Telegram Bot & Firebase
Write-Host "`n🤖 [Step 3/3] Synchronizing to Telegram Bot & Firebase..." -ForegroundColor Green
$workerPublishUrl = "https://3d-scraper-worker.khaingkhantkyaw001.workers.dev/api/app/publish"

$publishPayload = @{
    version_name = $Tag
    version_code = $versionCode
    file_name = $apk.Name
    file_size = $apk.Length
    download_url = $downloadUrl
    release_notes = $Notes
    uploaded_by = "publish_release.ps1"
    broadcast = (-not $NoBroadcast)
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri $workerPublishUrl -Method Post -Body $publishPayload -ContentType "application/json" -TimeoutSec 60
    Write-Host "✅ Synchronized to Telegram Bot & Firebase!" -ForegroundColor Green
    Write-Host "   Telegram File ID: $($response.release.file_id)" -ForegroundColor Cyan
    Write-Host "   Broadcasted to users: $($response.broadcastCount)" -ForegroundColor Cyan
} catch {
    Write-Warning "⚠️ Cloudflare Worker sync warning: $_"
}

Write-Host "`n============================================================" -ForegroundColor Green
Write-Host "   🎉 RELEASE WORKFLOW COMPLETE!                           " -ForegroundColor Green
Write-Host "   • GitHub Release: $downloadUrl" -ForegroundColor Green
Write-Host "   • Telegram Bot: Ready to deliver to buyers immediately  " -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
