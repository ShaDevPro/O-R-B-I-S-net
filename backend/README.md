# Backend OrbisNet - Télémétrie & Mises à Jour (Vercel)

Ce sous-projet est le serveur sans état (Serverless / Edge) pour OrbisNet, déployable gratuitement sur **Vercel**.

## Fonctionnalités

1. **Télémétrie Respectueuse de la Vie Privée (Zero-Knowledge)** :
   - Aucun contenu de message, aucun numéro de téléphone n'est transmis.
   - Calcul des utilisateurs uniques quotidiens et mensuels via HyperLogLog.
   - Détection automatique et gratuite des pays via les en-têtes Edge de Vercel (`x-vercel-ip-country`).
   - Suivi du taux d'utilisation de chaque composant (Appels, Stories, Cercle Famille, Messagerie, etc.).

2. **Système de Mise à Jour Obligatoire (Force Update)** :
   - Bloque immédiatement les versions obsolètes de l'application.
   - Configuration dynamique sans recompiler l'APK.
   - Notes de version multilingues (Français, Anglais, Arabe).

3. **Dashboard Web Administrateur (`/admin`)** :
   - Interface web sombre, moderne et réactive pour suivre les métriques en temps réel et piloter les mises à jour depuis un PC ou smartphone.

---

## Déploiement Gratuit sur Vercel (en 2 minutes)

### Méthode 1 : Via l'interface web de Vercel (Recommandé)
1. Rendez-vous sur [vercel.com](https://vercel.com) et connectez-vous avec votre compte GitHub.
2. Cliquez sur **"Add New..."** -> **"Project"**.
3. Importez votre dépôt contenant le dossier `backend` (ou faites pointer la racine du projet Vercel sur `backend`).
4. Dans les paramètres du projet Vercel, ajoutez l'intégration gratuite **Upstash Redis** (accessible directement dans l'onglet *Storage* de votre tableau de bord Vercel en 1 clic).
   - Les variables d'environnement `UPSTASH_REDIS_REST_URL` et `UPSTASH_REDIS_REST_TOKEN` seront injectées automatiquement.
5. *(Optionnel)* Définissez une variable d'environnement :
   - `ORBIS_ADMIN_KEY` : Votre mot de passe secret d'administration (défini dans les variables d'environnement Vercel).
6. Cliquez sur **"Deploy"**. Vercel vous fournira votre URL HTTPS (ex: `https://orbis-backend.vercel.app`).

### Méthode 2 : Via la ligne de commande Vercel CLI
```bash
npm install -g vercel
cd backend
vercel
```

---

## Configuration de l'Application Android

Une fois votre URL Vercel obtenue (ex: `https://mon-orbis-backend.vercel.app`) :
- Dans l'application Android OrbisNet, ouvrez la **Console Développeur** -> onglet **Télémétrie**.
- Renseignez votre URL de backend (ou conservez l'URL par défaut préconfigurée).
- L'application commencera immédiatement à transmettre ses pings anonymes et vérifiera les mises à jour.
