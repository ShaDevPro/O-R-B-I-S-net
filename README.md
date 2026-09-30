# 🌐 OrbisNet — Réseau Social & Messagerie Décentralisée Souveraine Nostr (WebSockets, Android Telecom & WebRTC E2EE)

[![Version](https://img.shields.io/badge/Version-v1.4.0_Stable-0284c7.svg?style=flat&logo=github)](https://github.com/ShaDevPro/O-R-B-I-S-net/releases)
[![Package](https://img.shields.io/badge/Package-com.sha.orbisnet-0284c7.svg?style=flat&logo=android)](https://github.com/ShaDevPro/O-R-B-I-S-net)
[![Protocole](https://img.shields.io/badge/Protocole-Nostr_(WebSockets_wss)-8b5cf6.svg?style=flat&logo=nostr)](https://nostr.com)
[![Cryptographie](https://img.shields.io/badge/Cryptographie-BIP--340_Schnorr_&_NIP--44_E2EE-ef4444.svg?style=flat&logo=lock)](https://en.wikipedia.org/wiki/End-to-end_encryption)
[![Appels](https://img.shields.io/badge/Appels-WebRTC_Telecom_Voix_%26_Vidéo_E2EE-10b981.svg?style=flat&logo=webrtc)](https://webrtc.org)
[![Plateforme](https://img.shields.io/badge/Plateforme-100%25_Android_Exclusif-10b981.svg?style=flat&logo=android)](https://android.com)
[![Apple iOS](https://img.shields.io/badge/Apple_iOS-NON_SUPPORTÉ-critical.svg?style=flat&logo=apple)](#-compatibilité-matérielle--exclusivité-android)
[![Langage](https://img.shields.io/badge/Langage-Kotlin_2.2-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/Interface-Jetpack_Compose_Material_3-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Moteur IA](https://img.shields.io/badge/IA_Locale-Guard--LLM_%26_Reply--LLM-f59e0b.svg?style=flat&logo=openai)](https://github.com/ShaDevPro/O-R-B-I-S-net)
[![Console Edge](https://img.shields.io/badge/Console_Edge-Vercel_Hébergée-black.svg?style=flat&logo=vercel)](https://orbis-net.vercel.app)
[![Telegram](https://img.shields.io/badge/Communauté-Telegram-2CA5E0.svg?style=flat&logo=telegram)](https://t.me/orbis_community)

---

> [!IMPORTANT]
> ### 🤖 Architecture 100% Android Native — Strictement Incompatible avec Apple iOS (iPhone)
> **OrbisNet est exclusivement conçu pour les smartphones Android.**  
> Il est **techniquement et fondamentalement incompatible avec Apple iOS (iPhone)** en raison des restrictions hermétiques de la sandbox d'Apple qui interdisent la gestion des connexions socket persistantes de fond, l'accès bas niveau au matériel et l'isolation matérielle requise par notre architecture souveraine.  
> **Si vous possédez un iPhone, OrbisNet ne peut pas fonctionner sur votre appareil.**

---

## 📖 Présentation Exécutive

**OrbisNet** (`com.sha.orbisnet`) est une plateforme souveraine de communication décentralisée et de réseau social libre propulsée par le **protocole décentralisé Nostr** via des connexions WebSockets persistantes sécurisées (`wss://`), combinée à un moteur d'appels voix/vidéo chiffré de bout en bout propulsé par **WebRTC et Android Telecom**.

OrbisNet fonctionne sur n'importe quel accès Internet — que ce soit en **Wi-Fi** ou via les **données mobiles cellulaires (Data 4G / 5G / GSM Data)**.  
**Il n'utilise aucun SMS cellulaire payant ni aucun appel vocal GSM traditionnel.**  
Toutes les discussions, publications, notes vocales, appels audio et vidéo transitent sous forme de paquets de données chiffrés de bout en bout via un maillage mondial de relais Nostr décentralisés et de flux directs WebRTC en pair à pair (P2P).

### Piliers Fondamentaux d'OrbisNet :

1. ⚡ **Messagerie Décentralisée Nostr (Zéro Serveur Central, Zéro SMS)** :  
   Échanges instantanés en ligne via les relais Nostr mondiaux (`wss://relay.damus.io`, `wss://nos.lol`, `wss://relay.primal.net`). Chiffrement de bout en bout des messages privés (DMs NIP-04 et NIP-44) garantissant une confidentialité mathématique totale.
2. 🔑 **Identité Cryptographique Souveraine (BIP-340 Schnorr)** :  
   Aucun numéro de téléphone, aucune carte SIM et aucune adresse email ne sont nécessaires pour créer un profil ou communiquer. Votre identité repose sur une paire de clés `secp256k1` (`npub` pour votre adresse publique, `nsec` pour votre clé secrète). Chaque message, réaction et post est signé cryptographiquement par Schnorr.
3. 📞 **Appels Vocaux & Vidéo Chiffrés E2EE (WebRTC + Core-Telecom)** :  
   Communication audio et vidéo haute fidélité en pair à pair direct via WebRTC (`JavaAudioDeviceModule`, AEC/NS matériel, H.264/Opus), intégrée au sous-système **Android Telecom (`ConnectionService`)** en mode `CAPABILITY_SELF_MANAGED`. Zéro appel GSM cellulaire, routage Bluetooth/Voiture natif, et immunité contre la coupure par les surcouches agressives (Vivo, Honor, Xiaomi, Samsung).
4. 🔔 **Réveil Silencieux FCM (Serverless Cloud Functions)** :  
   Impulsion haute priorité en mode **Data-Only** pour sortir instantanément les appareils en veille prolongée (Doze Mode) sans générer aucune notification en double.
5. 🛡️ **Diagnostic Constructeurs & Popups Automatiques** :  
   Configuration 100% automatisée pour les utilisateurs débutants via les boîtes de dialogue système Android natives au lancement, complétée par un outil de diagnostic constructeur pour déverrouiller l'autostart sur Vivo (FunTouch OS), Honor (MagicUI), Xiaomi (MIUI/HyperOS) et Samsung (One UI).
6. 📰 **Mur Social Public Kind 1 & Stories 24h Entre Amis** :  
   Publiez des notes publiques ouvertes sur le réseau décentralisé Nostr ou partagez des stories éphémères et des sondages interactifs limités exclusivement à vos cercles d'amis de confiance.
7. 🎙️ **Notes Vocales à Haute Densité** :  
   Enregistrement audio compressé avec ZLIB Deflater et lecture interactive avec forme d'onde tactile (*Waveform*) et sélecteur de vitesse (1.0x / 1.5x / 2.0x), acheminé en millisecondes sous forme de paquet de données chiffré.
8. 🧠 **Double Moteur IA Embarqué 100% Local (Guard-LLM & Reply-LLM)** :  
   Moteurs neuronaux propriétaires 100% Kotlin natif exécutés directement sur le processeur du smartphone (< 2 Mo, inférence < 10 ms, zéro requête externe). Détection en temps réel des tentatives de phishing/arnaques et génération de 3 suggestions de réponses rapides contextuelles (français, anglais, arabe).
9. ☁️ **Console Edge & Gestionnaire de Mises à Jour (Vercel)** :  
   Infrastructure Edge sans état hébergée sur Vercel ([https://orbis-net.vercel.app](https://orbis-net.vercel.app)) pour la diffusion instantanée des annonces de sécurité, la vérification d'intégrité SHA-256 des fichiers APK et la télémétrie anonyme (zéro donnée personnelle).
10. 🔒 **Sécurité Matérielle Forteresse** :  
    Protection au repos via l'enclave processeur Android KeyStore TEE, code PIN de détresse ouvrant un profil leurre inoffensif avec purge silencieuse des clés secrètes en cas de contrainte physique, et écrasement immédiat de la RAM (`fill(0)`).

---

## 🏗️ Architecture Globale d'OrbisNet

```mermaid
graph TD
    subgraph UI_Layer ["🎨 Interface Utilisateur Jetpack Compose (Material 3)"]
        Header["OrbisTopHeader : Statut des Relais + Recherche + Profil npub"]
        Nav["Barre de Navigation : Mur Nostr / Chats E2EE / Appels / Contacts / Paramètres"]
        TabSocial["📰 Mur Mondial Nostr & Stories Éphémères 24h"]
        TabChat["💬 Discussions Privées E2EE & Notes Vocales"]
        TabCall["📞 Appels Voix & Vidéo E2EE (Telecom + WebRTC)"]
        TabContacts["👥 Annuaire Souverain, Cercles & Certification QR"]
        TabSettings["⚙️ Paramètres, Clés npub/nsec, Diagnostic Constructeur & Guide"]
    end

    subgraph Nostr_Subsystem ["⚡ Moteur Décentralisé Nostr (WebSockets wss://)"]
        RelayPool["Gestionnaire de Relais wss:// (damus.io, nos.lol, primal.net)"]
        SchnorrSigner["Signataire Cryptographique BIP-340 Schnorr (secp256k1)"]
        DMEngine["Moteur de Messages Privés Chiffrés NIP-04 / NIP-44"]
        FeedEngine["Flux Public Kind 1, Réactions NIP-25 & Sondages"]
        RelayPool --> SchnorrSigner
        RelayPool --> DMEngine
        RelayPool --> FeedEngine
    end

    subgraph Telecom_WebRTC ["📞 Moteur d'Appels Natif Telecom & WebRTC P2P"]
        TelecomService["Android ConnectionService (CAPABILITY_SELF_MANAGED)"]
        WebRTCManager["OrbisWebRTCManager : JavaAudioDeviceModule + HW AEC/NS"]
        VideoEngine["Flux Vidéo H.264 Baseline P2P Multiplexé"]
        TurnRelays["Relais TURN Résilients (0xchat, Google, Cloudflare)"]
        FCMWakeup["OrbisFirebasePushHelper : Impulsion Réveil Doze Silencieuse"]
        TelecomService --> WebRTCManager
        WebRTCManager --> VideoEngine
        WebRTCManager --> TurnRelays
        FCMWakeup -.-> TelecomService
    end

    subgraph Security_Core ["🔒 Forteresse Cryptographique & Sécurité"]
        KeyStore["Enclave Matérielle Sécurisée Android KeyStore (TEE)"]
        NIP44Crypto["Chiffrement Authentifié NIP-44 & Chaîne de Clés PFS"]
        DuressEngine["Gestionnaire de Détresse : Profil Leurre & Purge des Clés"]
        MemoryPurge["Écrasement Volatile RAM : fill(0) après Déchiffrement"]
    end

    subgraph AI_Core ["🧠 Moteurs Neuronaux 100% Locaux (Kotlin Vectoriel)"]
        GuardLLM["Orbis Guard-LLM : Bouclier Anti-Arnaque & Analyse Sémantique"]
        ReplyLLM["Orbis Reply-LLM : Suggestions Contextuelles (FR / EN / AR)"]
        LocalLearning["Auto-Entraînement Embarqué : Poids Adaptatifs Scellés"]
        GuardLLM --> LocalLearning
        ReplyLLM --> LocalLearning
    end

    subgraph Edge_Cloud ["☁️ Infrastructure Souveraine Edge (Vercel)"]
        EdgeAPI["API Edge Vercel : Alertes de Sécurité & Annonces d'Urgence"]
        IntegrityCheck["Contrôle d'Intégrité SHA-256 & Mises à Jour APK"]
    end

    TabChat --> DMEngine
    TabSocial --> FeedEngine
    TabCall --> Telecom_WebRTC
    DMEngine --> NIP44Crypto
    SchnorrSigner --> KeyStore
    TabChat --> ReplyLLM
    TabChat --> GuardLLM
    TabSettings --> EdgeAPI
    EdgeAPI --> IntegrityCheck
```

---

## 🌟 Fonctionnalités Principales & Piliers Techniques

### 1. ⚡ Réseau Décentralisé Nostr & WebSockets
- **Connexion Multi-Relais Résiliente** : OrbisNet se connecte simultanément à une sélection configurable de relais WebSocket (`wss://relay.damus.io`, `wss://nos.lol`, etc.). Si un relais devient inaccessible, les autres prennent immédiatement le relais sans coupure.
- **Messagerie Privée E2EE (NIP-04 & NIP-44)** : Les conversations directes sont chiffrées de bout en bout. Seuls l'expéditeur et le destinataire détiennent les clés mathématiques permettant de déchiffrer les textes et médias.
- **Flux Public & Non-Censurable (Kind 1)** : Publication de notes courtes, partages de liens et fils de discussion ouverts, sans dépendance à une autorité centrale ni risque de bannissement arbitraire.

### 2. 🔑 Identité Souveraine par Clés Cryptographiques (BIP-340)
- **Zéro Inscription & Zéro Numéro de Téléphone** : La création d'un compte ne requiert aucune carte SIM, aucun SMS de validation et aucune adresse email.
- **Paires de Clés Déterministes** : Votre identité est votre clé publique `npub` (partagée avec vos correspondants). Votre clé secrète `nsec` reste scellée sur votre téléphone.
- **Signatures Schnorr Inviolables** : Chaque interaction est signée avec l'algorithme cryptographique Schnorr sur la courbe elliptique `secp256k1`.

### 3. 📞 Appels Vocaux & Vidéo Chiffrés E2EE (WebRTC + Android Telecom)
- **Sous-système Android Telecom (`ConnectionService`)** :
  - Déclaré en mode `CAPABILITY_SELF_MANAGED` : **100% VoIP Internet (Wi-Fi / 4G/5G), strictement aucun canal ou coût GSM**.
  - Reconnu par le noyau Android comme un appel système prioritaire : les surcouches constructeurs (Vivo FunTouch OS, Honor MagicUI, MIUI, One UI) ne coupent ni le micro ni le processus en arrière-plan.
  - Routage audio natif automatique de l'OS (écouteur, haut-parleur, oreillettes Bluetooth, kits mains-libres voiture).
- **Moteur Audio WebRTC Universel (`JavaAudioDeviceModule`)** :
  - Annulation d'écho acoustique matérielle (Hardware AEC) et suppression de bruit (Hardware NS).
  - Élimination des coupures de réseau : les bascules temporaires WiFi↔4G (`DISCONNECTED`) sont tolérées sans raccrochage.
  - Timeout automatique anti-gel d'écran de 20s en cas d'échec de traversée NAT/TURN.
- **Flux Vidéo H.264 Baseline** :
  - Encodage vidéo matériel direct P2P, multiplexé avec l'audio sur un seul socket UDP (`max-bundle`).
  - Prise en charge du basculement instantané caméra avant/arrière (`switchCamera()`).
- **Code Vocal Court SAS** :
  - Validation mutuelle par code de sécurité vocal court (*Short Authentication String*) pour éliminer tout risque d'attaque de l'homme du milieu (MITM).

### 4. 🔔 Réveil Silencieux FCM & Anti-Doublon
- **Message Haute Priorité Data-Only** :
  - Impulsion FCM silencieuse envoyée via Cloud Functions serverless légères lors de l'établissement de l'appel.
  - Réveille le processeur du smartphone en veille profonde (Doze Mode) pour reconnecter Nostr instantanément.
  - **Zéro notification en double** : n'affiche aucune bannière parasite dans la barre d'état ; seul le flux d'appel Telecom sonne.

### 5. 🛡️ Popups Natives Automatiques & Diagnostic Constructeurs
- **Expérience Néophyte Zéro-Configuration** :
  - Dès l'ouverture de l'application, les boîtes de dialogue système Android natives demandent les autorisations requises (Microphone, Caméra, Notifications, Exemption de veille permanente).
- **Écran de Diagnostic Constructeur (`OEMDiagnosticHelper`)** :
  - Détection automatique du modèle (Vivo, Honor, Xiaomi, Huawei, Samsung).
  - Liens profonds directs pour activer le démarrage automatique (*Autostart*) dans iManager (Vivo), le Gestionnaire de lancement (Honor/Huawei) ou SecurityCenter (Xiaomi).
  - Interface trilingue complète : **Français, Anglais, Arabe (RTL)**.

### 6. 📰 Mur Social, Stories 24h & Sondages
- **Publications Ouvertes ou Entre Amis** : Choisissez de diffuser vos messages sur le réseau mondial Nostr ou réservez la diffusion à vos cercles de confiance.
- **Stories Éphémères 24h** : Partagez des moments temporaires avec halo lumineux, compteur de vues et suppression automatique après 24 heures.
- **Sondages Décentralisés** : Créez des sondages interactifs avec décompte des votes en temps réel et signature cryptographique individuelle.

### 7. 🎙️ Notes Vocales Haute Densité
- **Compression Acoustique Avancée** : Codec vocal optimisé avec compression Deflater ZLIB.
- **Lecteur Waveform Interactif** : Défilement tactile de la forme d'onde, marqueur de progression précis et sélecteur de vitesse de lecture (1.0x / 1.5x / 2.0x).

### 8. 🧠 Double Moteur IA Embarqué 100% Local (Guard-LLM & Reply-LLM)
- **Orbis Guard-LLM (Bouclier Anti-Arnaque)** :
  - Analyse sémantique vectorisée en direct de vos discussions.
  - Détection immédiate des faux liens bancaires, du phishing et des arnaques avec classification tri-état (*Sûr*, *Suspect*, *Danger*).
- **Orbis Reply-LLM (Suggestions Intelligentes)** :
  - Génère 3 suggestions de réponses rapides, polies et contextuelles au-dessus du champ de saisie.
  - Inférence locale sur processeur en moins de 10 ms sans aucune consommation de données.
  - Support multilingue natif : Français, Anglais, Arabe.
- **Zéro Fuite de Données** : Les modèles neuronaux s'exécutent entièrement en local. Aucune donnée textuelle ni vecteur n'est jamais transmise à un serveur cloud.

### 9. ☁️ Console Edge Vercel & Mises à Jour Souveraines
- **Supervision Sans État** : Hébergée sur l'infrastructure Vercel Edge Network ([https://orbis-net.vercel.app](https://orbis-net.vercel.app)).
- **Annonces & Alertes d'Urgence** : Diffusion en direct d'avis de sécurité et de messages importants aux utilisateurs.
- **Contrôle d'Intégrité SHA-256** : Contrôle cryptographique systématique des fichiers APK officiels pour interdire toute falsification.

### 10. 🔒 Sécurité Forteresse en Profondeur
- **Android KeyStore (TEE)** : Clés privées conservées dans l'enclave matérielle isolée du processeur de votre smartphone (anti-extraction ADB).
- **Code PIN de Détresse & Profil Leurre** : La saisie d'un code PIN alternatif déverrouille instantanément un profil leurre vide tout en détruisant en silence les clés de session cryptographiques.
- **Écrasement RAM Volatile** : Les clés et tampons déchiffrés sont effacés de la mémoire vive (`fill(0)`) immédiatement après utilisation.
- **Coffre Chiffré V3** : Sauvegardes intégrales scellées avec PBKDF2 à 100 000 itérations et sel aléatoire de 128 bits.

---

## 🔒 Matrice Cryptographique & Sécurité

| Couche | Algorithme / Protocole | Détails d'Implémentation |
|---|---|---|
| **Identité & Signature** | **BIP-340 Schnorr / secp256k1** | Clés déterministes sur l'appareil, validation mathématique universelle |
| **Messagerie Privée** | **NIP-44 / NIP-04 (WebSockets wss)** | Chiffrement authentifié de bout en bout via relais décentralisés |
| **Appels Voix & Vidéo** | **WebRTC + Telecom + DTLS-SRTP** | Flux P2P direct chiffré, routage Telecom `SELF_MANAGED`, 0 GSM |
| **Réveil en Veille** | **FCM Data-Only + WakeLock** | Impulsion silencieuse haute priorité, Doze mode bypass, 0 doublon |
| **Stockage au Repos** | **Android KeyStore (TEE)** | Clé maîtresse matérielle ; protection absolue contre l'extraction mémoire ADB |
| **Sauvegardes du Coffre** | **PBKDF2 (100k itérations)** | Hachage ralenti avec sel aléatoire de 128 bits |
| **Moteurs d'IA Neuronale** | **Moteur Vectoriel Kotlin Natif** | Exécution 100% sur processeur local, 0 télémétrie, auto-apprentissage scellé |
| **Défense Anti-Contrainte** | **PIN de Détresse & Profil Leurre** | Déverrouillage d'un profil fictif et destruction silencieuse des clés |
| **Infrastructure Edge** | **Vercel Edge Network + SHA-256** | Diffusion des annonces critiques, validation d'intégrité, 0 donnée personnelle |

---

## 📊 Transparence Réseau & Sobriété en Données Mobiles

OrbisNet a été optimisé à l'octet près pour fonctionner avec une consommation minimale de données mobiles (4G/5G ou Wi-Fi) :

| Action Utilisateur | Consommation Données | Type de Transport | Détails Techniques |
|---|---|---|---|
| 💬 **Message Texte 1 à 1** | **< 1 Ko** | WebSockets Nostr (wss) | Chiffrement NIP-44 instantané via relais décentralisés |
| 📞 **Appel Vocal Chiffré E2EE** | **~25 Ko / sec** | WebRTC Telecom P2P | Flux audio Opus/JavaADM avec chiffrement DTLS-SRTP |
| 📹 **Appel Vidéo Chiffré E2EE** | **Adaptatif (selon débit)** | WebRTC Telecom P2P | Flux vidéo H.264 Baseline multiplexé P2P |
| 🗺️ **Position GPS Satellite** | **< 1 Ko** | WebSockets Nostr (wss) | Coordonnées géographiques compactes ~40 octets |
| 👍 **Réaction Emoji & Accusé** | **< 1 Ko** | Événement Nostr NIP-25 | Paquet allégé instantané |
| 📰 **Publication Mur & Stories** | **< 5 Ko** (texte/médias légers) | Événement Nostr Kind 1 | Diffusion sur relais Nostr ou envoi direct aux cercles d'amis |
| 🎙️ **Note Vocale (3 à 5 sec)** | **< 15 Ko** | WebSockets Nostr (wss) | Fichier audio compressé ZLIB Deflater |
| 🧠 **IA Guard-LLM & Reply-LLM** | **0 Ko (100% Hors Réseau)** | Processeur Local (CPU) | Inférence neuronale locale • Zéro octet transmis |

---

## 📥 Téléchargement & Installation

### 📋 Compatibilité Matérielle & Exclusivité Android
- **Système d'Exploitation** : 🤖 **100% Exclusif Android** (Android 8.0 Oreo / API 26 jusqu'à **Android 16** / API 36-37).
- **Constructeurs Validés** : Samsung (One UI 6-8), Vivo (FunTouch OS/OriginOS), Honor (MagicUI), Huawei (EMUI), Xiaomi/Redmi (MIUI/HyperOS).
- **Apple iOS / iPhone** : 🚫 **STRICTEMENT NON SUPPORTÉ** — Incompatible par conception technique.
- **Connexion Réseau** : Wi-Fi ou Données Mobiles cellulaires (4G / 5G / GSM Data). **Aucun forfait SMS ni crédit d'appel requis.**

### 🚀 Guide d'Installation Rapide
1. **Téléchargez l'APK Officiel Signé** :
   - Récupérez `OrbisNet.apk` depuis les [Releases GitHub](https://github.com/ShaDevPro/O-R-B-I-S-net/releases) ou la [Console Edge](https://orbis-net.vercel.app).
2. **Installation & Autorisations Natives** :
   - Ouvrez le fichier `.apk` et autorisez l'installation.
   - Dès l'ouverture, validez les boîtes de dialogue système natives d'Android (Microphone, Caméra, Notifications et Batterie).
3. **Connexion Immédiate** :
   - Rejoignez instantanément le réseau décentralisé Nostr grâce à votre clé `npub`, ou scannez le QR code d'un proche pour commencer à échanger en toute liberté.

---

## 📄 Licence & Droits d'Auteur

Conçu et développé avec une rigueur souveraine par **ShaDevPro**.  
© 2026 **OrbisNet — Le Réseau Hybride Souverain Nostr (WebSockets, Telecom & WebRTC)**. Tous droits réservés.
