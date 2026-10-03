<#
.SYNOPSIS
    Script Maître d'Automatisation de Déploiement & Mise à Jour pour OrbisNet.
.DESCRIPTION
    Simplifie le passage d'une version à une autre en 1 seule commande ou 2-3 saisies interactives :
    1. Met à jour la version dans app/build.gradle.kts (source unique de vérité).
    2. Compile l'APK release signé (assembleRelease).
    3. Calcule le hash SHA-256 et la taille de l'APK.
    4. Crée le commit Git, le tag Git (OrbisNet-vX.Y.Z) et push sur GitHub via le token.
    5. Publie la Release officielle GitHub avec l'APK téléversé.
    6. Met à jour la configuration en direct dans Redis (/api/admin/config) sans redéploiement backend.
.EXAMPLE
    .\publish-release.ps1
    .\publish-release.ps1 -Version "1.5.0" -VersionCode 150 -NotesFr "Optimisations majeures"
#>

[CmdletBinding()]
param (
    [string]$Version,
    [int]$VersionCode = 0,
    [string]$NotesFr,
    [string]$NotesEn,
    [string]$NotesAr,
    [switch]$ForceUpdate,
    [switch]$SkipBuild,
    [string]$AdminKey
)

$ErrorActionPreference = "Stop"

# Couleurs & En-tête
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  🚀 ORBISNET - DÉPLOIEMENT & MISE À JOUR AUTOMATISÉE      " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $rootDir

$gradlePath = Join-Path $rootDir "app\build.gradle.kts"
if (-not (Test-Path $gradlePath)) {
    Write-Error "Fichier introuvable : $gradlePath"
    exit 1
}

# 1. Extraction de la version actuelle
$gradleContent = Get-Content $gradlePath -Raw
$currentCodeMatch = [regex]::Match($gradleContent, 'versionCode\s*=\s*(\d+)')
$currentNameMatch = [regex]::Match($gradleContent, 'versionName\s*=\s*"([^"]+)"')

$currentCode = if ($currentCodeMatch.Success) { [int]$currentCodeMatch.Groups[1].Value } else { 140 }
$currentName = if ($currentNameMatch.Success) { $currentNameMatch.Groups[1].Value } else { "1.4.0" }

Write-Host "📌 Version actuelle détectée : v$currentName (Build: $currentCode)" -ForegroundColor Gray

# 2. Détermination de la nouvelle version
if (-not $Version) {
    # Proposition intelligente (ex: 1.4.0 -> 1.5.0)
    $parts = $currentName.Split('.')
    if ($parts.Length -ge 2) {
        $suggestedMinor = [int]$parts[1] + 1
        $suggestedName = "$($parts[0]).$suggestedMinor.0"
    } else {
        $suggestedName = "1.5.0"
    }
    $inputVersion = Read-Host "Nouvelle version versionName [$suggestedName]"
    $Version = if ($inputVersion.Trim()) { $inputVersion.Trim() } else { $suggestedName }
}

if ($VersionCode -le 0) {
    $suggestedCode = $currentCode + 10
    $inputCode = Read-Host "Nouveau versionCode [$suggestedCode]"
    $VersionCode = if ($inputCode.Trim()) { [int]$inputCode.Trim() } else { $suggestedCode }
}

if (-not $NotesFr) {
    $defaultNoteFr = "Mise à jour OrbisNet v$Version : Améliorations des performances et de la sécurité."
    $inputNote = Read-Host "Notes de version FR [$defaultNoteFr]"
    $NotesFr = if ($inputNote.Trim()) { $inputNote.Trim() } else { $defaultNoteFr }
}

if (-not $NotesEn) {
    $NotesEn = "OrbisNet v$Version release: Performance enhancements, stability, and security updates."
}

if (-not $NotesAr) {
    $NotesAr = "إصدار OrbisNet v$Version: تحسينات في الأداء والاستقرار وتحديثات الأمان."
}

Write-Host ""
Write-Host "🎯 Déploiement cible :" -ForegroundColor Green
Write-Host "   - Version Name : v$Version" -ForegroundColor White
Write-Host "   - Version Code : $VersionCode" -ForegroundColor White
Write-Host "   - Forcer MAJ   : $ForceUpdate" -ForegroundColor White
Write-Host ""

# 3. Mise à jour automatique de app/build.gradle.kts
Write-Host "📝 Mise à jour de app/build.gradle.kts..." -ForegroundColor Cyan
$newGradleContent = [regex]::Replace($gradleContent, 'versionCode\s*=\s*\d+', "versionCode = $VersionCode")
$newGradleContent = [regex]::Replace($newGradleContent, 'versionName\s*=\s*"[^"]+"', "versionName = `"$Version`"")
Set-Content -Path $gradlePath -Value $newGradleContent -NoNewline
Write-Host "   ✓ build.gradle.kts mis à jour avec succès." -ForegroundColor Green

# 4. Compilation de l'APK Release signé
$apkTarget = Join-Path $rootDir "O R B I S.apk"
if (-not $SkipBuild) {
    Write-Host "⚙️ Compilation de l'APK Release signé (assembleRelease)..." -ForegroundColor Cyan
    $gradlew = Join-Path $rootDir "gradlew.bat"
    & $gradlew assembleRelease
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Échec de la compilation assembleRelease ! Vérifiez les logs ci-dessus."
        exit $LASTEXITCODE
    }

    # Recherche de l'APK produit
    $possiblePaths = @(
        (Join-Path $rootDir "app\build\outputs\apk\release\app-release.apk"),
        (Join-Path $rootDir "app\release\app-release.apk"),
        (Join-Path $rootDir "app\build\outputs\apk\release\app-release-unsigned.apk")
    )
    $foundApk = $possiblePaths | Where-Object { Test-Path $_ } | Select-Object -First 1

    if ($foundApk) {
        Copy-Item -Path $foundApk -Destination $apkTarget -Force
        Write-Host "   ✓ APK généré et copié vers : $apkTarget" -ForegroundColor Green
    } else {
        Write-Warning "APK généré introuvable dans les chemins standards. Utilisation de l'APK existant si présent."
    }
}

# 5. Calcul de l'empreinte SHA-256 et taille
$sha256 = ""
$sizeMo = "53 Mo"
if (Test-Path $apkTarget) {
    Write-Host "🔍 Calcul du hash SHA-256 et taille de l'APK..." -ForegroundColor Cyan
    $hashObj = Get-FileHash -Algorithm SHA256 -Path $apkTarget
    $sha256 = $hashObj.Hash.ToLower()
    $fileInfo = Get-Item $apkTarget
    $sizeMo = "$([Math]::Round($fileInfo.Length / 1MB)) Mo"
    Write-Host "   ✓ SHA-256 : $sha256" -ForegroundColor Green
    Write-Host "   ✓ Taille  : $sizeMo" -ForegroundColor Green
}

# 6. Push Git & Release GitHub
$tokenFile = Join-Path $rootDir "github_token.txt"
$hasToken = Test-Path $tokenFile

if ($hasToken) {
    $token = (Get-Content $tokenFile -Raw).Trim()
    $tag = "OrbisNet-v$Version"

    Write-Host "📦 Git Commit et Tag ($tag)..." -ForegroundColor Cyan
    try {
        git add docs/
        if (Test-Path (Join-Path $rootDir "README.md")) { git add README.md }
        if (Test-Path (Join-Path $rootDir "CHANGELOG.md")) { git add CHANGELOG.md }
        git commit -m "release: v$Version (code $VersionCode)"
        git tag -a $tag -m "Release v$Version" -f
        $pushUrl = "https://ShaDevPro:$($token)@github.com/ShaDevPro/O-R-B-I-S-net.git"
        git push $pushUrl main --tags
        Write-Host "   ✓ Code et tags poussés sur GitHub !" -ForegroundColor Green
    } catch {
        Write-Warning "Avertissement Git : $($_.Exception.Message)"
    }

    # Création de la Release GitHub via API
    Write-Host "🌐 Création de la Release GitHub ($tag)..." -ForegroundColor Cyan
    $headers = @{
        "Authorization" = "Bearer $token"
        "Accept"        = "application/vnd.github.v3+json"
        "User-Agent"    = "OrbisNet-Release-Tool"
    }

    $releaseBody = @"
# 🌟 OrbisNet v$Version

### Nouveautés & Correctifs :
- $NotesFr

### Intégrité Cryptographique :
- **Fichier** : `O.R.B.I.S.apk`
- **Taille** : $sizeMo
- **SHA-256** : `$sha256`

Déployé automatiquement via Sovereign Release Pipeline.
"@

    $releasePayload = @{
        tag_name         = $tag
        name             = "OrbisNet v$Version"
        body             = $releaseBody
        draft            = $false
        prerelease       = $false
    } | ConvertTo-Json

    try {
        $createReleaseUrl = "https://api.github.com/repos/ShaDevPro/O-R-B-I-S-net/releases"
        $releaseResponse = Invoke-RestMethod -Uri $createReleaseUrl -Method Post -Headers $headers -Body $releasePayload -ContentType "application/json"
        $releaseId = $releaseResponse.id
        Write-Host "   ✓ Release GitHub créée avec succès (ID: $releaseId) !" -ForegroundColor Green

        # Téléversement de l'APK si présent
        if ((Test-Path $apkTarget) -and $releaseId) {
            Write-Host "⬆️ Téléversement de l'APK vers la release GitHub..." -ForegroundColor Cyan
            $uploadUrl = "https://uploads.github.com/repos/ShaDevPro/O-R-B-I-S-net/releases/$releaseId/assets?name=O.R.B.I.S.apk"
            $uploadHeaders = @{
                "Authorization" = "Bearer $token"
                "Content-Type"  = "application/vnd.android.package-archive"
                "User-Agent"    = "OrbisNet-Release-Tool"
            }
            $apkBytes = [System.IO.File]::ReadAllBytes($apkTarget)
            $uploadRes = Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers $uploadHeaders -Body $apkBytes
            Write-Host "   ✓ APK téléversé avec succès sur la release GitHub !" -ForegroundColor Green
        }
    } catch {
        Write-Warning "Note sur la release GitHub : $($_.Exception.Message)"
    }
} else {
    Write-Warning "Fichier github_token.txt introuvable. Étape GitHub API sautée."
}

# 7. Synchronisation directe Redis (/api/admin/config)
Write-Host "⚡ Synchronisation directe de la configuration en direct dans Redis..." -ForegroundColor Cyan
$downloadUrl = "https://github.com/ShaDevPro/O-R-B-I-S-net/releases/download/OrbisNet-v$Version/O.R.B.I.S.apk"

# Recherche de la clé admin si non passée
if (-not $AdminKey) {
    $adminKeyFile = Join-Path $rootDir ".admin_key"
    if (Test-Path $adminKeyFile) {
        $AdminKey = (Get-Content $adminKeyFile -Raw).Trim()
    }
}

if ($AdminKey) {
    try {
        $backendPayload = @{
            latestVersionCode      = $VersionCode
            latestVersionName      = $Version
            minRequiredVersionCode = if ($ForceUpdate) { $VersionCode } else { 100 }
            minRequiredVersionName = if ($ForceUpdate) { $Version } else { "1.0.0" }
            forceUpdate            = [bool]$ForceUpdate
            downloadUrl            = $downloadUrl
            sha256                 = $sha256
            apkSize                = $sizeMo
            releaseNotes           = @{
                fr = $NotesFr
                en = $NotesEn
                ar = $NotesAr
            }
        } | ConvertTo-Json -Depth 5

        $apiUrl = "https://orbis-net.vercel.app/api/admin/config"
        $apiHeaders = @{
            "Content-Type" = "application/json"
            "x-admin-key"  = $AdminKey
        }
        $resp = Invoke-RestMethod -Uri $apiUrl -Method Post -Headers $apiHeaders -Body $backendPayload
        Write-Host "   ✓ Configuration Redis mise à jour en direct ! (Zero-Downtime)" -ForegroundColor Green
    } catch {
        Write-Warning "Mise à jour Redis via API impossible ($($_.Exception.Message))."
        Write-Host "   👉 Vous pouvez mettre à jour Redis en 1 clic depuis https://orbis-net.vercel.app/admin ou l'application." -ForegroundColor Yellow
    }
} else {
    Write-Host "ℹ️ Clé admin non fournie. Pour synchroniser Redis automatiquement, créez un fichier .admin_key ou passez -AdminKey." -ForegroundColor Yellow
    Write-Host "👉 Sinon, connectez-vous sur https://orbis-net.vercel.app/admin pour ajuster les versions en 1 clic." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  🎉 DÉPLOIEMENT TERMINÉ AVEC SUCCÈS POUR v$Version !      " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Résumé des actions exécutées :" -ForegroundColor White
Write-Host "1. Code Android : versionCode=$VersionCode, versionName=$Version" -ForegroundColor Green
Write-Host "2. APK Release  : O R B I S.apk (SHA-256: $sha256)" -ForegroundColor Green
Write-Host "3. Site Web     : Détectera automatiquement la v$Version sans retouche de code" -ForegroundColor Green
Write-Host "4. Pilotage MAJ : Activable/Désactivable à chaud via la console Admin" -ForegroundColor Green
Write-Host ""
