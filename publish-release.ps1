<#
.SYNOPSIS
    Script Simplifie de Mise a Jour de Version et Poussee Git Double (Site Web + Backend).
.DESCRIPTION
    Adapte a votre workflow personnalise :
    1. Met a jour la version dans app/build.gradle.kts et backend/src/lib/redis.ts.
    2. Vous laisse generer l'APK Release dans Android Studio et creer votre Release sur GitHub.
    3. Effectue automatiquement les 2 poussees Git (Site Web + Backend) avec github_token.txt.
.EXAMPLE
    .\publish-release.ps1
    .\publish-release.ps1 -Version "1.5.0" -VersionCode 150
    .\publish-release.ps1 -PushOnly
    .\publish-release.ps1 -VersionOnly -Version "1.5.0"
#>

[CmdletBinding()]
param (
    [string]$Version,
    [int]$VersionCode = 0,
    [switch]$PushOnly,
    [switch]$VersionOnly
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  ORBISNET - GESTIONNAIRE DE VERSION & DEPLOIEMENT GIT    " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $rootDir

$gradlePath = Join-Path $rootDir "app\build.gradle.kts"
$backendRedisPath = Join-Path $rootDir "backend\src\lib\redis.ts"
$tokenFile = Join-Path $rootDir "github_token.txt"

# Verification du token
if (-not (Test-Path $tokenFile)) {
    Write-Error "Fichier github_token.txt introuvable a la racine : $tokenFile"
    exit 1
}
$token = (Get-Content $tokenFile -Raw).Trim()

# -------------------------------------------------------------------------
# ETAPE 1 : CHANGEMENT DE VERSION (si non PushOnly)
# -------------------------------------------------------------------------
if (-not $PushOnly) {
    if (-not (Test-Path $gradlePath)) {
        Write-Error "Fichier gradle introuvable : $gradlePath"
        exit 1
    }

    $gradleContent = Get-Content $gradlePath -Raw
    $currentCodeMatch = [regex]::Match($gradleContent, 'versionCode\s*=\s*(\d+)')
    $currentNameMatch = [regex]::Match($gradleContent, 'versionName\s*=\s*"([^"]+)"')

    $currentCode = if ($currentCodeMatch.Success) { [int]$currentCodeMatch.Groups[1].Value } else { 140 }
    $currentName = if ($currentNameMatch.Success) { $currentNameMatch.Groups[1].Value } else { "1.4.0" }

    Write-Host "[INFO] Version actuelle detectee : v$currentName (Build: $currentCode)" -ForegroundColor Gray

    if (-not $Version) {
        $parts = $currentName.Split('.')
        if ($parts.Length -ge 2) {
            $suggestedMinor = [int]$parts[1] + 1
            $suggestedName = "{0}.{1}.0" -f $parts[0], $suggestedMinor
        } else {
            $suggestedName = "1.5.0"
        }
        $inputVersion = Read-Host "Nouvelle version versionName [$suggestedName]"
        $Version = if ($inputVersion -and $inputVersion.Trim()) { $inputVersion.Trim() } else { $suggestedName }
    }

    if ($VersionCode -le 0) {
        $suggestedCode = $currentCode + 10
        $inputCode = Read-Host "Nouveau versionCode [$suggestedCode]"
        $VersionCode = if ($inputCode -and $inputCode.Trim()) { [int]$inputCode.Trim() } else { $suggestedCode }
    }

    Write-Host ""
    Write-Host "[1/2] Mise a jour des fichiers de version..." -ForegroundColor Cyan

    # A. Mise a jour app/build.gradle.kts
    $newGradleContent = [regex]::Replace($gradleContent, 'versionCode\s*=\s*\d+', "versionCode = $VersionCode")
    $newGradleContent = [regex]::Replace($newGradleContent, 'versionName\s*=\s*"[^"]+"', "versionName = `"$Version`"")
    Set-Content -Path $gradlePath -Value $newGradleContent -NoNewline
    Write-Host "   [OK] app/build.gradle.kts mis a jour (v$Version, code $VersionCode)" -ForegroundColor Green

    # B. Mise a jour backend/src/lib/redis.ts
    if (Test-Path $backendRedisPath) {
        $redisContent = Get-Content $backendRedisPath -Raw
        $newRedisContent = [regex]::Replace($redisContent, 'latestVersionCode:\s*\d+', "latestVersionCode: $VersionCode")
        $newRedisContent = [regex]::Replace($newRedisContent, 'latestVersionName:\s*"[^"]+"', "latestVersionName: `"$Version`"")
        Set-Content -Path $backendRedisPath -Value $newRedisContent -NoNewline
        Write-Host "   [OK] backend/src/lib/redis.ts mis a jour (v$Version, code $VersionCode)" -ForegroundColor Green
    }

    Write-Host ""
    Write-Host "----------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host "-> Version appliquee : v$Version (code $VersionCode)" -ForegroundColor Yellow
    Write-Host "-> Action requise de votre part :" -ForegroundColor Yellow
    Write-Host "   1. Generez votre APK Release dans Android Studio (Build > Generate Signed APK)." -ForegroundColor White
    Write-Host "   2. Creez la Release sur GitHub et deposez-y votre APK." -ForegroundColor White
    Write-Host "----------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host ""

    if ($VersionOnly) {
        Write-Host "[FIN] Option -VersionOnly demandee. Les poussees Git ne sont pas executees." -ForegroundColor Cyan
        exit 0
    }

    $confirmPush = Read-Host "Voulez-vous lancer les 2 poussees Git (Site Web + Backend) maintenant ? [O/n]"
    if ($confirmPush -and ($confirmPush.Trim().ToLower() -eq "n" -or $confirmPush.Trim().ToLower() -eq "non")) {
        Write-Host "[INFO] Poussees Git reportees. Vous pourrez les lancer plus tard avec : .\publish-release.ps1 -PushOnly" -ForegroundColor Yellow
        exit 0
    }
} else {
    # Mode PushOnly : detection de la version actuelle pour les messages de commit
    $gradleContent = Get-Content $gradlePath -Raw
    $currentNameMatch = [regex]::Match($gradleContent, 'versionName\s*=\s*"([^"]+)"')
    $Version = if ($currentNameMatch.Success) { $currentNameMatch.Groups[1].Value } else { "latest" }
    Write-Host "[INFO] Mode -PushOnly actif pour la version v$Version" -ForegroundColor Gray
}

# -------------------------------------------------------------------------
# ETAPE 2 : POUSSEE 1 - SITE WEB (DEPOT PRINCIPAL)
# -------------------------------------------------------------------------
Write-Host ""
Write-Host "[POUSSEE 1/2] Poussee du Site Web sur GitHub (O-R-B-I-S-net)..." -ForegroundColor Cyan
Set-Location $rootDir
try {
    git add docs/
    if (Test-Path "README.md") { git add README.md }
    if (Test-Path "CHANGELOG.md") { git add CHANGELOG.md }
    git commit -m "docs: release version v$Version" 2>$null
    
    $websiteUrl = "https://ShaDevPro:$token@github.com/ShaDevPro/O-R-B-I-S-net.git"
    git push $websiteUrl main
    Write-Host "   [OK] Site Web pousse avec succes sur GitHub Pages !" -ForegroundColor Green
} catch {
    Write-Warning "Erreur lors de la poussee du Site Web : $($_.Exception.Message)"
}

# -------------------------------------------------------------------------
# ETAPE 3 : POUSSEE 2 - BACKEND (DEPOT VERCEL)
# -------------------------------------------------------------------------
Write-Host ""
Write-Host "[POUSSEE 2/2] Poussee du Backend sur GitHub (OrbisNetBack)..." -ForegroundColor Cyan
$backendDir = Join-Path $rootDir "backend"
if (Test-Path $backendDir) {
    Set-Location $backendDir
    try {
        git add src/
        git commit -m "feat(config): release version v$Version" 2>$null
        
        $backendUrl = "https://ShaDevPro:$token@github.com/ShaDevPro/OrbisNetBack.git"
        git push $backendUrl main
        Write-Host "   [OK] Backend pousse avec succes sur Vercel !" -ForegroundColor Green
    } catch {
        Write-Warning "Erreur lors de la poussee du Backend : $($_.Exception.Message)"
    }
} else {
    Write-Warning "Dossier backend introuvable : $backendDir"
}

Set-Location $rootDir

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  LES DEUX POUSSEES GIT SONT TERMINEES AVEC SUCCES !      " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""
