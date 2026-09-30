# 📘 Guide Maître : Déploiement & Mise à Jour Forcée OrbisNet (Sans Casse)

> **Document de Référence Opérationnelle**  
> Ce guide détaille la procédure exacte, standardisée et sans risque de régression pour déployer une nouvelle version d'OrbisNet et piloter la mise à jour forcée (ou optionnelle) à travers l'écosystème complet : **APK Android**, **Backend Vercel**, **Site Web GitHub Pages** et **Console Superviseur**.

---

## 🧭 1. Comprendre l'Architecture & la Règle Mathématique

Dans l'application OrbisNet, le blocage de l'écran en cas de mise à jour est évalué par la formule suivante dans `AppUpdateManager.kt` :

$$\text{isForced} = (\text{myVersionCode} < \text{minRequiredVersionCode}) \lor (\text{forceUpdate} \land \text{myVersionCode} < \text{latestVersionCode})$$

### Les 3 États Possibles :
1. **À jour (`UpdateStatus.UpToDate`)** :  
   `myVersionCode >= latestVersionCode`. Aucun dialogue n'apparaît.
2. **Mise à jour optionnelle (`UpdateStatus.OptionalUpdateAvailable`)** :  
   `myVersionCode < latestVersionCode` mais $\text{isForced}$ est **FAUX**.  
   L'utilisateur peut continuer d'utiliser l'application et voit une bannière discrète d'invitation.
3. **Mise à jour obligatoire stricte (`UpdateStatus.ForceUpdateRequired`)** :  
   $\text{isForced}$ est **VRAI**.  
   Un dialogue plein écran infranchissable bloque l'accès à l'application tant que la nouvelle version n'est pas installée.

### ⚠️ Le Piège Mortel à Éviter Absolument :
Si `minRequiredVersionCode` est fixé à la nouvelle version (ex: 140) alors que vous vouliez simplement une mise à jour facultative, l'ancienne version (ex: 130) sera **bloquée de force**, même si la case `forceUpdate` est décochée !  
👉 **Règle d'or** :  
- **Quand la mise à jour n'est PAS forcée** : `forceUpdate = false` ET `minRequiredVersionCode = 100` (ou $\le$ version actuelle).
- **Quand la mise à jour EST obligatoire stricte** : `forceUpdate = true` ET `minRequiredVersionCode = latestVersionCode`.

---

## 📱 2. Étape 1 : Préparation de l'APK (Client Android)

### A. Incrémentation des versions dans `app/build.gradle.kts`
Modifier les deux clés dans `defaultConfig` :
```kotlin
defaultConfig {
    applicationId = "com.sha.orbisnet"
    minSdk = 24
    targetSdk = 36
    versionCode = 140       // <- Incrémenter (ex: 130 -> 140)
    versionName = "1.4.0"   // <- Nouvelle version (ex: "1.3.0" -> "1.4.0")
}
```

### B. Configuration par défaut dans `RemoteAppConfig.kt`
Fichier : `app/src/main/java/com/sha/orbis/update/RemoteAppConfig.kt`
- Mettre à jour `latestVersionCode = 140`, `latestVersionName = "1.4.0"`.
- Définir `minRequiredVersionCode = 100`, `minRequiredVersionName = "1.0.0"`, `forceUpdate = false` pour le fallback local sans risque de blocage.
- Adapter `downloadUrl` vers la nouvelle release GitHub.
- Mettre à jour les notes de version `releaseNotesFr`, `releaseNotesEn`, `releaseNotesAr`.

### C. Liens de partage de l'application
Vérifier et mettre à jour les URL de téléchargement dans :
- `app/src/main/java/com/sha/orbis/ui/settings/ShareAppDialog.kt`
- `app/src/main/java/com/sha/orbis/ui/profile/UserProfileWallScreen.kt`
Format attendu :  
`https://github.com/ShaDevPro/O-R-B-I-S-net/releases/download/OrbisNet-vX.Y.Z/O.R.B.I.S.apk`

### D. Régénération du QR Code APK
Générer l'image PNG du QR Code pour le nouveau lien de téléchargement et la placer dans :  
`app/src/main/res/drawable/qr_code_download.png`

### E. Compilation et Validation
```powershell
# 1. Vérification stricte Kotlin (rapide, sans empaquetage)
.\gradlew.bat compileDebugKotlin

# 2. Exécution des tests unitaires
.\gradlew.bat testDebugUnitTest

# 3. Génération de l'APK Release signé
.\gradlew.bat assembleRelease
```
L'APK généré se trouvera dans `app/release/app-release.apk` (ou `O R B I S.apk`).

### F. Calcul du Hash SHA-256 de l'APK
```powershell
Get-FileHash -Algorithm SHA256 "app/release/app-release.apk"
# Noter le hash hexadécimal retourné pour le site web
```

---

## 🌐 3. Étape 2 : Site Web & Documentation (`docs/`)

Le site web hébergé sur GitHub Pages fournit le téléchargement direct aux utilisateurs.

### A. Fichiers à modifier :
1. **`docs/index.html`** :
   - Numéro de version (ex: `v1.4.0`).
   - Taille exacte de l'APK (ex: `53 Mo`).
   - Hash SHA-256 dans le bloc d'intégrité souveraine.
   - Lien direct du bouton Télécharger vers la release GitHub.
2. **`docs/app.js`** :
   - Mettre à jour la constante de version et le lien de téléchargement.
3. **`README.md` et `CHANGELOG.md`** :
   - Ajouter l'entrée détaillée de la nouvelle version avec historique.
4. **QR Codes du site** :
   - `docs/qr-code.png`
   - `docs/qr-code.svg`

### B. Commandes Git pour publier le site web (avec Token) :

Le token GitHub PAT est stocké localement à la racine dans `github_token.txt` (protégé et ignoré par `.gitignore`).

```powershell
# 1. Depuis la racine du projet OrbisNet
git add docs/ README.md CHANGELOG.md
git commit -m "docs: release version 1.4.0 with updated SHA-256 and apk size"

# 2. Push direct avec le token GitHub (sans invite d'authentification)
$token = (Get-Content "github_token.txt" -Raw).Trim()
git push "https://ShaDevPro:$($token)@github.com/ShaDevPro/O-R-B-I-S-net.git" main
```

### C. ⚠️ Résolution du Blocage GitHub Pages (`id-token: write`) :
Si le déploiement GitHub Pages échoue avec l'annotation rouge :  
`Ensure GITHUB_TOKEN has permission "id-token: write"`

**Cause** : Par défaut, GitHub restreint les permissions de workflow en mode *Read-only*. L'action de déploiement `actions/deploy-pages` a besoin de l'autorisation d'écriture OIDC.

**Solution permanente appliquée** :
Les permissions de workflow du dépôt ont été basculées en `write` via l'API GitHub :
```powershell
$token = (Get-Content "github_token.txt" -Raw).Trim()
$headers = @{ "Authorization" = "Bearer $token"; "Accept" = "application/vnd.github.v3+json"; "Content-Type" = "application/json" }
$body = @{ "default_workflow_permissions" = "write"; "can_approve_pull_request_reviews" = $true } | ConvertTo-Json
Invoke-RestMethod -Uri "https://api.github.com/repos/ShaDevPro/O-R-B-I-S-net/actions/permissions/workflow" -Method Put -Headers $headers -Body $body
```
*(Ou manuellement : GitHub repo -> Settings -> Actions -> General -> Workflow permissions -> Cocher "Read and write permissions" -> Save).*

Dès ce réglage actif, chaque `git push` sur `main` compile et déploie le site sur `https://shadevpro.github.io/O-R-B-I-S-net/` en moins de 40 secondes avec statut vert ✅.

---

## ⚡ 4. Étape 3 : Backend Next.js & Upstash Redis (`backend/`)

Le backend fournit l'API `/api/config` interrogée en direct par toutes les applications en circulation.

### A. Fichiers clés :
1. **`backend/src/lib/redis.ts`** :
   ```typescript
   export const DEFAULT_APP_CONFIG: AppConfig = {
     minRequiredVersionCode: 100, // Sécurité : ne jamais bloquer par défaut
     minRequiredVersionName: "1.0.0",
     latestVersionCode: 140,
     latestVersionName: "1.4.0",
     forceUpdate: false, // Facultatif par défaut
     downloadUrl: "https://github.com/ShaDevPro/O-R-B-I-S-net/releases/download/OrbisNet-v1.4.0/O.R.B.I.S.apk",
     releaseNotes: {
       fr: "Note FR...",
       en: "Note EN...",
       ar: "Note AR..."
     }
   };
   ```
2. **`backend/src/app/api/config/route.ts`** :
   - Entête HTTP : `Cache-Control: no-store, no-cache, max-age=0` (évite la mise en cache par Vercel Edge).
   - Règle de sécurité : si `forceUpdate` est `false`, `minRequiredVersionCode` est toujours bridé à `100` pour empêcher tout blocage accidentel.

### B. Validation & Déploiement Vercel (avec Token) :
```powershell
cd "D:\Android projects\Orbis\OrbisNet\backend"
npm run build
git add src/
git commit -m "feat(config): update release configuration for v1.4.0"

# Push avec le token GitHub vers le dépôt distant
$token = (Get-Content "..\github_token.txt" -Raw).Trim()
git push "https://ShaDevPro:$($token)@github.com/ShaDevPro/OrbisNetBack.git" main
```
*Le push sur la branche `main` déclenche le redéploiement automatique en 30 secondes sur Vercel.*

---

## 🛡️ 5. Étape 4 : Pilotage via la Console Superviseur (Web ou App)

L'URL d'administration : `https://<votre-domaine-backend>/admin` (ou onglet Superviseur dans l'application).

### Procédure de Déploiement en 2 Temps (Zero-Downtime) :

```
[ Étape A : Déploiement Doux ]
├── Version Code Minimale  : 100 (v1.0.0)
├── Dernière Version Dispo : 140 (v1.4.0)
└── [ ] Bloquer l'accès    : DÉCOCHÉ
    └── Résultat : Les utilisateurs voient l'invitation à mettre à jour, mais peuvent toujours utiliser l'application.

         ▼ (Après vérification que l'APK se télécharge bien)

[ Étape B : Bascule Obligatoire Stricte ]
├── Version Code Minimale  : 140 (v1.4.0)
├── Dernière Version Dispo : 140 (v1.4.0)
└── [X] Bloquer l'accès    : COCHÉ 🔴
    └── Résultat : Toutes les versions antérieures à la 1.4.0 sont bloquées par le dialogue infranchissable.
```

### 🚨 Procédure d'Urgence (Rollback immédiat) :
Si un bug critique survient sur la nouvelle version :
1. Rendez-vous sur la console `/admin`.
2. **Décochez simplement la case** : `🔴 Bloquer l'accès à l'application si la version est obsolète`.
3. Cliquez sur **Sauvegarder les modifications**.
4. **Effet immédiat** : En moins de 3 secondes, l'API renvoie `minRequiredVersionCode = 100` et `forceUpdate = false`. Tous les utilisateurs bloqués retrouvent instantanément accès à leur application sans réinstallation.

---

## 📋 6. Checklist de Déploiement Rapide (Anti-Oubli)

| Étape | Action | Fichier(s) | Validé |
|:---|:---|:---|:---:|
| 1 | Incrémenter `versionCode` et `versionName` | `app/build.gradle.kts` | [ ] |
| 2 | Aligner le fallback client | `RemoteAppConfig.kt` | [ ] |
| 3 | Mettre à jour l'URL APK dans les partages | `ShareAppDialog.kt`, `UserProfileWallScreen.kt` | [ ] |
| 4 | Compiler et passer les tests | `.\gradlew.bat compileDebugKotlin testDebugUnitTest` | [ ] |
| 5 | Compiler l'APK release signé | `.\gradlew.bat assembleRelease` | [ ] |
| 6 | Calculer le hash SHA-256 de l'APK | `Get-FileHash app/release/app-release.apk` | [ ] |
| 7 | Mettre à jour taille + hash + version | `docs/index.html`, `docs/app.js` | [ ] |
| 8 | Publier la release sur GitHub | Tag `OrbisNet-vX.Y.Z` + upload APK | [ ] |
| 9 | Pousser la mise à jour du site web | `git push origin main` (racine) | [ ] |
| 10 | Mettre à jour et builder le backend | `backend/src/lib/redis.ts` + `npm run build` | [ ] |
| 11 | Déployer le backend sur Vercel | `git push origin main` (dans `backend/`) | [ ] |
| 12 | Vérifier dans la Console Superviseur | Phase A (Doux) $\rightarrow$ Phase B (Strict) | [ ] |
