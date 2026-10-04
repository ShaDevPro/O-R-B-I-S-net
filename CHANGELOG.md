# 📋 Changelog

All notable changes to the **ORBIS** sovereign offline messaging and social ecosystem will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [v1.5.0] - 2026-10-03

### 🚀 Major Highlights

- **✨ Fil d'Actualité Repensé (Next-Gen Social Feed & Cards)**:
  - Nouvelles cartes de publication épurées au design moderne, avec gestion fluide des médias et intégration visuelle optimisée.
  - Aperçus de liens externes (Instagram, YouTube, Web) instantanés sans blocage de threads UI.

- **⚡ Réactions en Temps Réel & Synchronisation Instantanée (Real-Time Reactions)**:
  - Panneau de réactions latéral synchronisé de manière réactive — les compteurs de réactions et de commentaires se mettent à jour instantanément sans recharger.

- **🛡️ Moteur de Confiance Web of Trust Décentralisé (Web of Trust Engine)**:
  - Système de certification à 4 piliers : certification face-à-face via QR Code cryptographique, score de réputation décentralisé, badges de vérification (Or, Bleu, Vert) intégrés aux profils.
  - Support multilingue natif complet des badges (Français, Anglais, Arabe RTL).

- **📲 Génération Dynamique des QR Codes de Partage (Dynamic Share QR Code)**:
  - Les codes QR de partage d'application pointent désormais dynamiquement vers la dernière version officielle released.

### 🔧 Bug Fixes & Security

- **🔒 Authentification & Sécurité Administrateur**:
  - Consolidation de la vérification cryptographique des rôles de supervision.
- **📞 Stabilité du Journal d'Appels & Télécom**:
  - Fiabilité renforcée de la synchronisation d'historique et des métadonnées d'appels WebRTC.

---

## [v1.4.0] - 2026-09-30

### 🚀 Major Highlights

- **⚡ Optimisations Majeures du Fil d'Actualité (Feed & External Posts)**:
  - Correction définitive du gel / crash lors de la présence de posts extra-Orbis (Instagram, TikTok, YouTube, Web) avec suppression de l'exception `measure is called on a deactivated node`.
  - Analyse linéaire ultra-rapide des métadonnées HTML sans expressions régulières lourdes ni blocage des threads de calcul.
  - Décodage optimisé des miniatures d'aperçu réduisant la consommation mémoire de 8 Mo à moins de 400 Ko.

- **📞 Barre de Navigation Épurée du Journal d'Appels (Call History Navigation)**:
  - Rénovation de la navigation "Tous / Manqués" : suppression du fond gris terne au profit d'un arrière-plan constant et mise en valeur par élargissement net de la bordure vert émeraude (2.5dp) à la sélection.
  - Badges de décompte harmonisés et ergonomie tactile préservée sans décalage de mise en page.

- **📲 Partage d'Application & Code QR v1.4.0 (Share App & QR Code)**:
  - Mise à jour de l'URL de téléchargement direct vers la release officielle v1.4.0 et génération des codes QR haute lisibilité intégrés.

### 🔧 Bug Fixes & Stability

- **🛡️ Watchdog & Diagnostics**:
  - Enrichissement du traceur de gel UI (`FeedDebugTracker`) avec capture de pile même sur Android 16.
  - Validation du pipeline de mise à jour forcée instantanée vers la version 1.4.0.

---

## [v1.3.0] - 2026-09-23

### 🚀 Major Highlights

- **💬 Discussions Privées (Private Conversations)**:
  - Possibilité d'initier des discussions privées chiffrées de bout en bout avec n'importe quel contact Nostr.

- **👁️ Masquage de Publications dans le Fil d'Actualité (Post Hiding)**:
  - Fonctionnalité permettant de masquer des publications indésirables directement depuis le feed social.

### 🔧 Bug Fixes & Stability

- **📞 Stabilité Appels 4G/4G (4G Call Stability)**:
  - Correction du bug de coupure d'appel lorsque les deux interlocuteurs sont en 4G — ajout de STUN discovery, backoff de reconnexion et timeout étendu.

- **🔄 Correction Doublons de Conversations (Duplicate Conversations Fix)**:
  - Résolution du bug créant des conversations dupliquées lors du partage externe vers l'application.

- Corrections mineures de bugs et améliorations de performance.

---

## [v1.2.0] - 2026-09-21

### 🚀 Major Highlights

- **⚡ Standby & Deep Sleep Resilience (Doze Mode & OEM Task Killers)**:
  - Permanent sovereign background listener (`NostrForegroundService`) maintained 24/7 with automatic keep-alive watchdog and boot recovery.
  - Native integration of Android 14+ `FOREGROUND_SERVICE_PHONE_CALL` preventing system kills on incoming VoIP calls.
  - Full-Screen Intent attached to call alerts: wakes up the CPU, turns on the locked screen and rings instantly.
  - Automatic detection and setup assistant for aggressive OEM task killers (`OemAutoStartHelper`): Xiaomi (MIUI/HyperOS), Samsung (OneUI deep sleep), Huawei/Honor (EMUI), Oppo/Realme (ColorOS), Vivo, OnePlus, Transsion.

- **💬 Modernized Conversation Interface & Bubbles (WhatsApp Refinement)**:
  - Distinct sender (periwinkle violet `#5E6BB2`) and receiver (soft lavender gray `#EEF0F8`) color palettes.
  - Symmetrical rounded geometry (16.dp), clean horizontal date dividers and adaptive voice note audio waves.

- **❤️ Floating WhatsApp-Style Emoji Reactions**:
  - Interactive floating reaction pill overlapping bubbles with dynamic emoji grouping and total reaction count badge.

- **✓✓ Live Blue Read Receipts (Accusé de lecture)**:
  - Real-time double checkmarks: Sent (single gray ✓), Delivered (double gray ✓✓), Read (double WhatsApp vivid blue ✓✓ `#38BDF8`).
  - Automatic cryptographic read receipt emission over Nostr (Kind 20003).

---

## [v1.1.0] - 2026-09-05

### 🚀 Major Highlights

- **📞 End-to-End Encrypted (E2EE) Voice Calls via GSM SMS Signaling**:
  - Direct peer-to-peer audio streaming encrypted with military-grade **AES-256-GCM** keys and zero central cloud servers.
  - Compact offline cellular signaling via SMS protocols (`ORB:CO:` call offer, `ORB:CA:` call accept, `ORB:CE:` call terminate).
  - **SAS (Short Authentication String)**: 4-digit visual security code displayed simultaneously on both devices to verify session integrity against Man-in-the-Middle (MITM) attacks and IMSI-catchers.
  - **Zero-Credit GSM Carrier Fallback**: If a contact does not have SMS balance to reply with the session handshake, Orbis provides a direct 1-tap fallback to standard cellular GSM phone calls (100% free for the receiver).
  - **Dead-Peer Hangup Watchdog**: Immediate UDP packet bursts upon hangup and a 4.5-second silence timeout watchdog to cleanly terminate inactive connections and preserve battery life.

- **👥 Sovereign Social Network ("Friends Only" Privacy Architecture)**:
  - Private social wall & feed strictly isolated between confirmed friends and trusted circles.
  - 24-hour disappearing stories with glowing multi-color ring indicators.
  - Decentralized polls with real-time signed SMS vote aggregation, percentage calculation, and duplicate vote prevention.
  - Emoji reactions (❤️, 🔥, 👏, 💡, 🛡️) and threaded comment discussions.
  - Removed all misleading open/public broadcast terminology in favor of sovereign circle-based sharing.

- **📱 Hardware Dual-SIM & Multi-Profile Integration**:
  - Live on-avatar SIM slot indicator badge (`SIM 1` / `SIM 2`).
  - Instant line switching with segregated cryptographic keys, contacts, and message stores.

- **🎙️ High-Compression Voice Notes via SMS**:
  - Compressed microphone recording (AMR/AAC) compressed with ZLIB Deflater into compact SMS payloads.
  - Interactive audio player featuring an animated waveform scrubber, tactile seeking, and playback speed switcher (1.0x / 1.5x / 2.0x).

- **📖 In-App Modular Help Center**:
  - Offline searchable help center with 7 interactive categories (*Getting Started*, *E2EE Messaging*, *Voice Calls*, *Social Wall & Stories*, *Dual SIM*, *Security & Vault*, *Troubleshooting*).
  - Full trilingual support (**English**, **Français**, **العربية** with native RTL).

- **📊 Cellular Quota & Expense Tracker**:
  - Real-time tracking of daily and monthly cellular SMS usage with transparency breakdown (1 SMS per message/GPS/reaction, 2 SMS for call handshake, 0 SMS during voice conversation).

---

### 🔒 Cryptography & Security

- **Local Master Encryption**: RSA-2048 identity keys backed by hardware Android KeyStore (TEE).
- **Session Sealing**: AES-256-GCM authenticated encryption with unique per-message nonces and SHA-256 HMAC integrity seals.
- **Perfect Forward Secrecy (PFS)**: Double Ratchet key exchange with active memory zeroing (`fill(0)`).
- **Anti-Spam & Contact Privacy**: Unknown incoming SMS messages are sequestered in a dedicated requests queue with cryptographic key verification.

---

### 📦 Artifact Details

| Property | Value |
| :--- | :--- |
| **Release Tag** | `ORBIS-v1.1.0` |
| **Package Name** | `com.sha.orbis` |
| **Binary Filename** | `O.R.B.I.S.apk` |
| **Download URL** | [Download APK](https://github.com/orbisoffline-cloud/ORBIS/releases/download/ORBIS-v1.1.0/O.R.B.I.S.apk) |
| **File Size** | `4.64 MB` (4,644,842 bytes) |
| **SHA-256 Checksum** | `6b983af8c9b139d39d220fb435f1c04c10e190a9817455c90d0886199bea96d9` |
| **Minimum Android** | Android 8.0 (API level 26) |
| **Target Android** | Android 15 (API level 35) |

---

## [v1.0.0] - Initial Release

- Initial release of ORBIS Sovereign Offline Messaging platform.
- Cellular SMS protocol engine and Double Ratchet cryptographic core.
- Jetpack Compose Material 3 sovereign UI.
