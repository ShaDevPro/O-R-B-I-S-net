/**
 * OrbisNet — Sovereign Nostr & GSM Hybrid Network — Master Application Script
 * Propulsé par ShaDevPro
 * Includes: i18n Dictionary (FR/EN/AR), Search Filter, Accordions, Theme Toggle, RTL Handling.
 */

document.addEventListener('DOMContentLoaded', () => {

  // =========================================================================
  // 1. i18n Localization Dictionary (FR / EN / AR)
  // =========================================================================
  const translations = {
    fr: {
      "nav.features": "Fonctionnalités",
      "nav.security": "Sécurité & Cryptographie",
      "nav.help": "Centre d'aide",
      "nav.quota": "Quota & Réseau",
      "nav.download": "Télécharger",
      "nav.get_app": "Obtenir l'APK",

      "hero.pill": "Protocole Hybride Décentralisé • Nostr Relays & GSM SMS Hors-Ligne",
      "hero.title_part1": "L'Internet Libre & Décentralisé.",
      "hero.title_part2": "En Ligne avec Nostr. Hors-Ligne avec GSM.",
      "hero.subtitle": "La première plateforme souveraine combinant le réseau décentralisé Nostr (WebSockets) et le transport cellulaire direct GSM SMS. Chiffrement militaire AES-256-GCM, Double Ratchet, signatures Schnorr BIP-340 et IA 100% locale.",
      "hero.download_btn": "Télécharger OrbisNet v1.1.0",
      "hero.guide_btn": "Explorer le Guide & FAQ",
      "hero.telegram_btn": "Communauté Telegram",
      "dl.btn_telegram": "Communauté Telegram",
      "dl.btn_github": "Dépôt Web & Releases",
      "dl.checksum_title": "Empreinte Cryptographique",
      "dl.copy_btn": "Copier",
      "dl.copied_btn": "✓ Copié !",

      "badges.encryption_title": "BIP-340 & Double Ratchet",
      "badges.encryption_sub": "Schnorr & AES-256-GCM",
      "badges.offline_title": "Hybride Nostr + GSM",
      "badges.offline_sub": "Relais WebSockets & Repli SMS",
      "badges.hardware_title": "Android KeyStore TEE",
      "badges.hardware_sub": "Enclave Matérielle Sécurisée",

      "mockup.peer_name": "Nostr Peer • npub1...",
      "mockup.status": "● Connecté • Relais Nostr + GSM Prêt",
      "mockup.msg1": "⚡ Connecté au relais décentralisé wss://relay.damus.io. Signatures Schnorr BIP-340 actives.",
      "mockup.msg2": "Parfait ! En cas de coupure Internet, bascule automatique instantanée en GSM SMS chiffré.",
      "mockup.gps": "🗺️ GPS: 33.5731° N, 7.5898° W",
      "mockup.msg3": "Appel vocal E2EE prêt • Bouclier IA Guard-LLM actif 🛡️",
      "mockup.placeholder": "Message chiffré Nostr / SMS...",

      "features.tag": "ARCHITECTURE HYBRIDE",
      "features.title": "La Souveraineté Numérique Totale",
      "features.desc": "La puissance d'un réseau mondial décentralisé (Nostr) couplée à l'invulnérabilité d'un protocole direct hors-ligne (GSM SMS).",
      "features.f1_title": "Messagerie Hybride Nostr & SMS P2P",
      "features.f1_text": "Échanges instantanés en ligne via relais Nostr décentralisés (DMs NIP-04/NIP-44), avec basculement automatique et imperceptible sur SMS GSM chiffré (Double Ratchet PFS) dès que le réseau internet fait défaut.",
      "features.f2_title": "Identité Nostr & Signatures BIP-340",
      "features.f2_text": "Identité cryptographique souveraine (paires de clés npub/nsec). Aucune adresse e-mail ni numéro de téléphone requis pour l'accès Nostr. Chaque publication et message est signé numériquement par Schnorr (secp256k1).",
      "features.f3_title": "Appels Vocaux & Vidéo Chiffrés E2EE",
      "features.f3_text": "Appels WebRTC P2P chiffrés de bout en bout avec signalisation Nostr ou SMS hors-ligne, streaming audio/vidéo direct, vérification du code de sécurité SAS anti-interception et bascule d'urgence sur appel GSM standard.",
      "features.f4_title": "Flux Social Mondial & Stories 24h",
      "features.f4_text": "Publiez des notes publiques (Kind 1), des sondages interactifs et des stories éphémères sur le réseau mondial Nostr ou limitez la diffusion exclusivement à vos cercles d'amis de confiance en P2P chiffré.",
      "features.f5_title": "Stéganographie SMS Invisible",
      "features.f5_text": "En mode secours SMS, dissimule les octets chiffrés sous forme de caractères Unicode invisibles de largeur nulle au milieu d'un texte anodin, contournant tout filtrage ou censure par l'opérateur.",
      "features.f6_title": "Coffre Chiffré V3 & Migration P2P",
      "features.f6_text": "Sauvegarde intégrale dérivée par PBKDF2 (100 000 itérations), nettoyage intelligent du stockage et migration de smartphone à smartphone sans aucune dépendance au cloud centralisé.",
      "features.f7_title": "Notes Vocales & Waveform Interactive",
      "features.f7_text": "Compression ZLIB/Deflater ultra-dense, scrubbing tactile fluide et sélecteur de vitesse de lecture (1.0x / 1.5x / 2.0x), transmis instantanément sur Nostr ou segmentés par SMS.",
      "features.f8_title": "Extraction OTP & Filtre Intelligent",
      "features.f8_text": "Détection instantanée et copie 1-clic des codes 2FA/bancaires reçus par SMS, avec priorisation en haut de liste et isolation des spams sans aucun accès internet requis.",
      "features.f9_title": "Console Edge & Mises à Jour Souveraines",
      "features.f9_text": "Synchronisation décentralisée avec l'infrastructure Edge Vercel (https://orbis-net.vercel.app) pour les annonces d'urgence, vérification d'intégrité SHA-256 et télémétrie de santé anonymisée.",
      "features.f_guard_title": "Orbis Guard-LLM : Bouclier Anti-Arnaque",
      "features.f_guard_text": "Analyse sémantique vectorisée en temps réel de vos SMS. Détection des faux liens bancaires, smishing et arnaques avec classification tri-état (Sûr, Suspect, Danger) et blocage 1-clic.",
      "features.f_reply_title": "Orbis Reply-LLM : Suggestions Intelligentes",
      "features.f_reply_text": "Génération instantanée de 3 réponses rapides, polies et contextuelles au-dessus de votre saisie dans le chat chiffré et les SMS classiques, en français, anglais et arabe.",
      "features.f_ai_engine_title": "Moteur IA Vectorisé 100% CPU Local",
      "features.f_ai_engine_text": "Modèles neuronaux propriétaires 100% Kotlin natif (< 2 Mo, inférence < 10 ms). Auto-apprentissage continu sur l'appareil à partir de vos retours, sans aucun serveur tiers ni fuite de données.",

      "security.tag": "ARCHITECTURE FORTERESSE",
      "security.title": "Sécurité Multi-Couches en Profondeur",
      "security.desc": "Chaque couche d'OrbisNet a été conçue pour résister à la censure, aux coupures d'accès, aux écoutes réseau et aux extractions physiques.",
      "security.s1_title": "Signatures Schnorr BIP-340 & secp256k1",
      "security.s1_text": "Authentification cryptographique native Nostr : chaque événement et message direct est signé numériquement par votre clé privée Schnorr, garantissant une intégrité mathématique absolue.",
      "security.s2_title": "Double Ratchet & Perfect Forward Secrecy",
      "security.s2_text": "Renouvellement automatique et irréversible des clés de session après chaque message. Même en cas de compromission d'une clé éphémère, le passé et le futur restent scellés.",
      "security.s3_title": "Stockage au Repos via Android KeyStore TEE",
      "security.s3_text": "Les bases de données Room locales et clés privées sont scellées par une clé matérielle AES-GCM gérée dans la zone sécurisée TEE de votre processeur (anti-extraction ADB).",
      "security.s4_title": "Dérivation de Coffre PBKDF2 100k Itérations",
      "security.s4_text": "Vos archives de sauvegarde `.orbis` résistent aux attaques par force brute grâce au hachage ralenti PBKDF2 combiné à un sel aléatoire cryptographique de 128 bits.",
      "security.s5_title": "Appels Vocaux & Vidéo Chiffrés avec Code SAS",
      "security.s5_text": "Balisage audio/vidéo chiffré en AES-256-GCM, authentification vocale par code court SAS anti-interception et coupure garantie sous 4.5s (Dead-Peer Watchdog).",
      "security.s6_title": "Signature Numérique & Anti-Altération",
      "security.s6_text": "Chaque trame Nostr, paquet SMS et publication est signée numériquement. L'intégrité du paquet est validée mathématiquement avant exécution pour bloquer toute falsification.",
      "security.s7_title": "Écrasement RAM & Zéro-Trace Volatile",
      "security.s7_text": "Dès leur lecture ou émission, les clés de dérivation éphémères et fragments décodés sont écrasés en mémoire vive via `fill(0)` pour interdire toute extraction forensique.",
      "security.s8_title": "Watchdog Temps Réel & Rupture d'Écoute",
      "security.s8_text": "Un chien de garde actif coupe automatiquement les sessions vocales et détruit les canaux éphémères après 4.5s de silence ou en cas d'anomalie réseau pour garantir l'étanchéité absolue.",
      "security.s9_title": "Moteur IA Vectorisé 100% Hors-Ligne",
      "security.s9_text": "Les deux LLM (Guard & Reply) s'exécutent 100% en local via un moteur matriciel Kotlin natif. Aucune donnée d'entraînement, SMS ou empreinte vectorielle ne transite sur le réseau.",
      "security.s10_title": "Banc de Test & Benchmark Matériel Dédié",
      "security.s10_text": "Outil de mesure temps réel de la latence CPU (< 1 ms), du débit d'inférence et de la RAM, avec activation granulaire désactivée par défaut pour un consentement souverain.",
      "security.s11_title": "Code PIN de Détresse & Profil Leurre Anti-Contrainte",
      "security.s11_text": "Système de défense physique (Duress Manager) : la saisie d'un code secret alternatif déverrouille un environnement leurre tout en purgeant silencieusement les clés de chiffrement.",
      "security.s12_title": "Télémétrie Edge Vercel & Vérification SHA-256",
      "security.s12_text": "Backend Edge souverain (https://orbis-net.vercel.app) pour la diffusion des annonces d'urgence, vérification des signatures APK par SHA-256 et diagnostic réseau sans identifiant personnel.",

      "help.tag": "GUIDE OFFICIEL & CENTRE D'AIDE",
      "help.title": "Comment Fonctionne OrbisNet ?",
      "help.desc": "Découvrez le guide officiel pour maîtriser l'hybridation Nostr, le repli cellulaire GSM et la sécurité souveraine.",
      "help.search_placeholder": "Rechercher un sujet (ex: nostr, repli, gsm, appels, clés, relais, ia)...",
      "help.cat_all": "Tous",
      "help.cat_start": "🚀 Démarrage",
      "help.cat_nostr": "⚡ Nostr & Relais",
      "help.cat_chat": "💬 Messagerie",
      "help.cat_calls": "📞 Appels E2EE",
      "help.cat_ai": "🧠 IA & LLM",
      "help.cat_sec": "🔐 Sécurité",
      "help.cat_sys": "⚙️ Système & GSM",

      "topics.t_telegram_title": "Comment recevoir les mises à jour et rejoindre la communauté ?",
      "topics.t_telegram_content": "Rejoignez notre communauté Telegram officielle (t.me/orbis_community) ou consultez le site web pour télécharger les nouvelles versions APK signées en direct, recevoir les alertes de sécurité et participer aux discussions et retours d'expérience.",
      "topics.t1_title": "Comment fonctionne l'architecture hybride Nostr + GSM SMS ?",
      "topics.t1_content": "Lorsque vous disposez d'une connexion Internet (Wi-Fi ou données mobiles), OrbisNet se connecte aux relais Nostr décentralisés mondiaux via WebSockets pour des échanges instantanés et gratuits (Kind 1, NIP-44 DMs). Dès qu'Internet est indisponible, en panne ou censuré, l'application bascule instantanément et automatiquement sur le réseau cellulaire GSM SMS chiffré de bout en bout (AES-256-GCM + Double Ratchet).",
      "topics.t2_title": "Qu'est-ce que le protocole Nostr et les clés npub / nsec ?",
      "topics.t2_content": "Nostr est un protocole de communication décentralisé, sans serveur central ni autorité de contrôle. Votre compte est constitué d'une paire de clés cryptographiques : votre clé publique (npub) qui sert d'adresse partagée, et votre clé privée (nsec) qui reste sur votre appareil pour signer vos messages (BIP-340 Schnorr). Aucun email ni numéro de téléphone n'est requis pour créer ou utiliser un compte Nostr.",
      "topics.t3_title": "Comment fonctionne l'appairage et l'échange de clés de sécurité ?",
      "topics.t3_content": "Lors de la première prise de contact, les utilisateurs s'échangent leurs clés de chiffrement soit par scan de QR Code direct (instantané et hors-ondes), soit par échange automatique sécurisé via relais Nostr ou SMS. Une fois le canal validé, un canal chiffré de bout en bout inviolable avec Perfect Forward Secrecy est établi.",
      "topics.t_call_e2ee_title": "Comment fonctionnent les appels vocaux et vidéo chiffrés E2EE ?",
      "topics.t_call_e2ee_content": "L'initiation de l'appel utilise une trame de signalisation WebRTC via relais Nostr (en ligne) ou SMS GSM compact (hors-ligne). Le flux audio/vidéo est diffusé en pair à pair (P2P) chiffré de bout en bout en AES-256-GCM. Un code court SAS (Short Authentication String) permet de vérifier verbalement l'absence d'interception.",
      "topics.t_call_credit_title": "Que se passe-t-il si un utilisateur n'a pas de connexion Internet ni de crédit SMS lors d'un appel ?",
      "topics.t_call_credit_content": "Si Internet est indisponible et que le solde SMS est épuisé, OrbisNet détecte immédiatement l'état et propose de basculer vers un appel GSM cellulaire classique standard. En réception, l'utilisateur hors-ligne peut décrocher gratuitement selon les tarifs habituels de son opérateur téléphonique.",
      "topics.t_call_sas_title": "Qu'est-ce que le code SAS pendant un appel ?",
      "topics.t_call_sas_content": "Le code SAS (Short Authentication String) est calculé à partir des empreintes cryptographiques de la session et s'affiche sur les deux écrans. En lisant ce code à voix haute au début de la conversation, les deux interlocuteurs confirment mathématiquement l'absence totale d'interception ou de fausse antenne relais.",
      "topics.t_call_hangup_title": "Comment est garanti le raccrochage et la fin d'appel ?",
      "topics.t_call_hangup_content": "Lors de la fin d'appel, OrbisNet envoie un signal de fermeture immédiat et active un chien de garde (Dead-Peer Watchdog). En cas de coupure brutale du réseau ou de perte de signal, la session est coupée automatiquement après 4.5 secondes de silence pour préserver la batterie et la confidentialité.",
      "topics.t4_title": "Le partage GPS nécessite-t-il une connexion Data mobile ?",
      "topics.t4_content": "Non ! La puce satellite GPS de votre smartphone fonctionne sans aucune connexion Internet ni données mobiles. OrbisNet extrait vos coordonnées satellite exactes et les transmet dans un message chiffré de 1 seul SMS (ou via relais Nostr si en ligne), ouvrant instantanément la carte chez votre contact.",
      "topics.t5_title": "Comment fonctionnent le mur social, les stories et les sondages ?",
      "topics.t5_content": "Sur le réseau Nostr, vous pouvez publier des notes publiques (Kind 1), des sondages interactifs et des stories éphémères visibles par l'ensemble du réseau décentralisé. Vous pouvez également basculer en mode 'Entre Amis' pour restreindre la diffusion exclusivement à vos cercles de confiance confirmés via P2P chiffré.",
      "topics.t6_title": "Comment la Stéganographie protège-t-elle contre la censure ?",
      "topics.t6_content": "La stéganographie dissimule la charge utile binaire chiffrée sous forme de caractères Unicode invisibles (largeur zéro) insérés dans une phrase d'apparence totalement banale. Pour un opérateur ou un tiers espion, le SMS ressemble à une conversation ordinaire.",
      "topics.t7_title": "Quelle est la consommation réelle en données et SMS ?",
      "topics.t7_content": "En ligne (relais Nostr via Wi-Fi ou 4G/5G), vous ne consommez aucun SMS (0 SMS) et seulement quelques kilo-octets de données. En mode hors-ligne, un message texte, un emoji de réaction ou un partage GPS ne consomment que 1 seul SMS. Sur un forfait avec SMS illimités, le coût est totalement nul.",
      "topics.t8_title": "L'application OrbisNet est-elle disponible sur iPhone (Apple iOS) ?",
      "topics.t8_content": "Non, OrbisNet est une exclusivité stricte Android. Le système fermé d'Apple (sandbox iOS) interdit formellement à toute application tierce d'intercepter les trames GSM SMS en arrière-plan, de gérer le matériel modem ou de servir de client SMS de téléphonie par défaut. Seul l'écosystème ouvert d'Android permet d'offrir une véritable souveraineté de communication hybride et hors-ligne.",
      "topics.t_guard_title": "Comment fonctionne Orbis Guard-LLM contre les arnaques et spams ?",
      "topics.t_guard_content": "Orbis Guard-LLM analyse en temps réel les SMS entrants grâce à des embeddings vectoriels locaux. Il détecte les stratagèmes d'urgence, fausses alertes bancaires, faux numéros SIM et isole les domaines frauduleux (tout en distinguant les abréviations innocentes comme 'M.Pierre'). Les messages douteux sont isolés dans l'onglet 'Spam & bloqués' avec alerte visuelle et option de blocage immédiat.",
      "topics.t_reply_title": "Comment Orbis Reply-LLM propose-t-il des réponses intelligentes hors-ligne ?",
      "topics.t_reply_content": "Orbis Reply-LLM analyse le fil de conversation et l'intention du dernier message (salutations, questions, logistique, urgence, remerciements) pour générer 3 pastilles de réponses rapides et polies au-dessus du clavier. L'inférence s'exécute en moins de 10 ms sans aucune connexion Internet ni appel réseau.",
      "topics.t_ai_learning_title": "Comment les modèles IA s'auto-entraînent-ils sans risque pour ma vie privée ?",
      "topics.t_ai_learning_content": "L'entraînement s'effectue exclusivement sur votre appareil (on-device continual learning). Lorsque vous bloquez un spam ou sélectionnez des suggestions, les poids synaptiques locaux s'adaptent dynamiquement dans le stockage chiffré de l'application. Aucune donnée textuelle, empreinte vectorielle ou statistique n'est envoyée sur un quelconque serveur.",
      "topics.t_edge_title": "À quoi sert la console Edge Vercel (https://orbis-net.vercel.app) ?",
      "topics.t_edge_content": "L'infrastructure Edge hébergée sur Vercel fournit un point de synchronisation souverain et ultra-rapide pour vérifier l'intégrité des mises à jour APK (hash SHA-256), diffuser les annonces critiques ou alertes de sécurité d'urgence, et afficher l'état de santé du réseau avec zéro donnée d'identification personnelle (zéro PII).",
      "topics.t_legal_title": "Politique de Confidentialité & Conditions d'Utilisation",
      "topics.t_legal_content": "OrbisNet est une infrastructure hybride souveraine. Aucune donnée personnelle n'est collectée ou transmise à des tiers. Les communications sont chiffrées de bout en bout (AES-256-GCM / BIP-340 / Double Ratchet). En tant que logiciel fourni « en l'état », chaque utilisateur est légalement et exclusivement responsable de son utilisation et de la conformité avec les lois de sa juridiction.",

      "quota.tag": "TRANSPARENCE RÉSEAU",
      "quota.title": "Consommation Réseau Nostr & Quota SMS",
      "quota.desc": "OrbisNet optimise chaque octet pour offrir une gratuité totale en ligne et une sobriété maximale en mode hors-ligne.",
      "quota.th_action": "Action Réalisée",
      "quota.th_cost": "Coût Réseau",
      "quota.th_details": "Détails Techniques",
      "quota.row1_title": "Message Texte & DMs (Mode En Ligne)",
      "quota.row1_cost": "0 SMS (Data < 1 Ko)",
      "quota.row1_desc": "WebSockets décentralisés Nostr • Chiffrement NIP-44 / AES-GCM • Instantané",
      "quota.row_fallback_title": "Message Texte 1 à 1 (Mode Hors-Ligne GSM)",
      "quota.row_fallback_cost": "1 SMS",
      "quota.row_fallback_desc": "Chiffré AES-256-GCM + Double Ratchet PFS + compression ZLIB",
      "quota.row_call_title": "Signalisation Appel Vocal & Vidéo E2EE",
      "quota.row_call_cost": "0 SMS (Nostr) / 2 SMS (GSM)",
      "quota.row_call_desc": "Échange de clés de session • 0 SMS consommé pendant le streaming audio/vidéo",
      "quota.row2_title": "Partage de Position GPS Satellite",
      "quota.row2_cost": "0 SMS (Nostr) / 1 SMS (GSM)",
      "quota.row2_desc": "Coordonnées géographiques ultra-compactes ~40 octets",
      "quota.row3_title": "Réactions Emoji & Accusés de Réception",
      "quota.row3_cost": "0 SMS (Nostr) / 1 SMS (GSM)",
      "quota.row3_desc": "Événement Nostr NIP-25 ou trame d'acquittement GSM compacte",
      "quota.row4_title": "Publication Sociale & Stories 24h",
      "quota.row4_cost": "0 SMS (Nostr) / P2P Direct",
      "quota.row4_desc": "Diffusion sur relais Nostr décentralisés ou P2P direct entre cercles de confiance",
      "quota.row5_title": "Note Vocale Audio (3 à 5 secondes)",
      "quota.row5_cost": "0 SMS (Nostr) / 5 à 12 SMS (GSM)",
      "quota.row5_desc": "Flux audio AMR/AAC compressé ZLIB • Instantané en ligne ou multi-segments par SMS",
      "quota.row_ai_title": "Bouclier IA Guard-LLM & Suggestions Reply-LLM",
      "quota.row_ai_cost": "0 SMS • 0 Ko Data",
      "quota.row_ai_desc": "Inférence neuronale 100% CPU locale • Zéro requête distante • Zéro télémétrie",

      "dl.title": "Téléchargez OrbisNet dès Aujourd'hui",
      "dl.desc": "Reprenez le contrôle de vos communications. Téléchargez l'application souveraine hybride sans magasin centralisé.",
      "dl.btn_apk": "Télécharger l'APK Officiel",
      "dl.qr_badge": "Scan Rapide",
      "dl.qr_title": "Installation Directe Smartphone",
      "dl.qr_desc": "Scannez ce QR Code avec l'appareil photo de votre smartphone Android pour lancer le téléchargement immédiat.",

      "legal.modal_title": "Confidentialité & Conditions",
      "legal.badge": "Souverain • Nostr & GSM Hybride",
      "legal.tab_privacy": "Confidentialité",
      "legal.tab_terms": "Conditions & Légal",
      "legal.p1_title": "1. Architecture Décentralisée & Zéro Profilage",
      "legal.p1_desc": "OrbisNet ne dispose d'aucun serveur central d'écoute ou de profilage. Les échanges transitent via des relais décentralisés Nostr ou directement de smartphone à smartphone via le réseau GSM cellulaire.",
      "legal.p2_title": "2. Cryptographie de Bout en Bout Matérielle",
      "legal.p2_desc": "Toutes les communications privées sont chiffrées avec AES-256-GCM, Double Ratchet (PFS), signatures BIP-340 Schnorr et clés secp256k1 scellées dans l'Android KeyStore TEE matériel.",
      "legal.p3_title": "3. Stockage Exclusivement Local & Souverain",
      "legal.p3_desc": "L'historique de vos discussions, clés secrètes et archives est conservé exclusivement sur votre appareil. Vous pouvez exporter une sauvegarde chiffrée (PBKDF2) ou détruire vos données à tout instant.",
      "legal.p4_title": "4. Réseau Nostr & Métadonnées Cellulaires",
      "legal.p4_desc": "Sur Nostr, vos publications publiques sont relayées par les serveurs WebSocket choisis. En mode de repli GSM, les paquets SMS chiffrés transitent par votre opérateur téléphonique, soumis aux métadonnées télécoms habituelles.",
      "legal.t1_title": "1. Licence d'Utilisation Personnelle & Fourniture « En l'état »",
      "legal.t1_desc": "OrbisNet est un logiciel souverain distribué « en l'état », sans garantie expresse ou implicite de disponibilité ininterrompue ou d'adéquation à un usage particulier.",
      "legal.t2_title": "2. Responsabilité Légale de l'Utilisateur",
      "legal.t2_desc": "Vous reconnaissez être seul responsable de l'usage fait de l'application, de la garde de vos clés privées (nsec / identifiants) et du respect des lois en vigueur dans votre pays.",
      "legal.t3_title": "3. Décharge de Responsabilité des Développeurs",
      "legal.t3_desc": "Les créateurs et contributeurs d'OrbisNet déclinent toute responsabilité civile ou pénale quant aux utilisations illicites, frauduleuses ou détournées du protocole.",
      "legal.t4_title": "4. Fonctionnalités d'Urgence (SOS & GPS)",
      "legal.t4_desc": "Les fonctionnalités de détresse SOS et de partage GPS satellite sont des outils de solidarité de pair à pair et ne remplacent en aucun cas les services officiels de secours d'urgence.",
      "legal.btn_close": "Compris & Accepter",
      "legal.open_modal_btn": "Consulter les Conditions & Confidentialité",

      "footer.privacy": "Confidentialité",
      "footer.terms": "Conditions & Légal",
      "footer.tagline": "Réseau social et messagerie hybride souveraine Nostr & GSM propulsé par ShaDevPro."
    },

    en: {
      "nav.features": "Features",
      "nav.security": "Security & Cryptography",
      "nav.help": "Help Center",
      "nav.quota": "Quota & Network",
      "nav.download": "Download",
      "nav.get_app": "Get APK",

      "hero.pill": "Decentralized Hybrid Protocol • Nostr Relays & Offline GSM SMS",
      "hero.title_part1": "The Sovereign & Free Internet.",
      "hero.title_part2": "Online with Nostr. Offline with GSM.",
      "hero.subtitle": "The world's first sovereign platform fusing decentralized Nostr WebSockets with direct GSM SMS cellular fallback. Military-grade AES-256-GCM, Double Ratchet, BIP-340 Schnorr signatures, and 100% on-device AI.",
      "hero.download_btn": "Download OrbisNet v1.1.0",
      "hero.guide_btn": "Explore Guide & FAQ",
      "hero.telegram_btn": "Telegram Community",
      "dl.btn_telegram": "Telegram Community",
      "dl.btn_github": "Web & Release Repo",
      "dl.checksum_title": "Cryptographic Checksum",
      "dl.copy_btn": "Copy",
      "dl.copied_btn": "✓ Copied!",

      "badges.encryption_title": "BIP-340 & Double Ratchet",
      "badges.encryption_sub": "Schnorr & AES-256-GCM",
      "badges.offline_title": "Hybrid Nostr + GSM",
      "badges.offline_sub": "WebSocket Relays & SMS Fallback",
      "badges.hardware_title": "Android KeyStore TEE",
      "badges.hardware_sub": "Hardware-Backed Enclave",

      "mockup.peer_name": "Nostr Peer • npub1...",
      "mockup.status": "● Connected • Nostr Relays + GSM Ready",
      "mockup.msg1": "⚡ Connected to decentralized wss://relay.damus.io. BIP-340 Schnorr signatures active.",
      "mockup.msg2": "Great! If internet drops, instant automatic fallback to encrypted GSM SMS kicks in.",
      "mockup.gps": "🗺️ GPS: 33.5731° N, 7.5898° W",
      "mockup.msg3": "E2EE voice call ready • Guard-LLM AI shield active 🛡️",
      "mockup.placeholder": "Encrypted Nostr / SMS message...",

      "features.tag": "HYBRID ARCHITECTURE",
      "features.title": "Total Digital Sovereignty",
      "features.desc": "The power of a decentralized global network (Nostr) combined with the resilience of an off-grid cellular protocol (GSM SMS).",
      "features.f1_title": "Hybrid Nostr & SMS P2P Messaging",
      "features.f1_text": "Instant online messaging via decentralized Nostr relays (NIP-04/NIP-44 DMs), with seamless automatic fallback to encrypted GSM SMS (Double Ratchet PFS) whenever internet connectivity drops.",
      "features.f2_title": "Nostr Identity & BIP-340 Signatures",
      "features.f2_text": "Sovereign cryptographic identity (npub/nsec keypairs). No email or phone number required for Nostr access. Every post and message is mathematically signed using Schnorr (secp256k1).",
      "features.f3_title": "Encrypted E2EE Voice & Video Calls",
      "features.f3_text": "Peer-to-peer WebRTC calls end-to-end encrypted with Nostr or offline SMS signaling, direct streaming, SAS anti-interception verification, and automatic cellular voice fallback.",
      "features.f4_title": "Global Social Feed & 24h Stories",
      "features.f4_text": "Publish public notes (Kind 1), interactive polls, and ephemeral 24h stories on the global Nostr network, or restrict distribution strictly to trusted peer circles via encrypted P2P.",
      "features.f5_title": "Invisible SMS Steganography",
      "features.f5_text": "In SMS fallback mode, conceals encrypted bytes as invisible zero-width Unicode characters inside ordinary text, bypassing telecom censorship.",
      "features.f6_title": "V3 Encrypted Vault & P2P Migration",
      "features.f6_text": "Full PBKDF2 (100,000 iterations) backup, intelligent storage cleaner, and seamless phone-to-phone migration without centralized cloud storage.",
      "features.f7_title": "Voice Notes & Interactive Waveform",
      "features.f7_text": "Ultra-dense ZLIB compression, responsive touch scrubbing, and smooth playback speed switching (1.0x / 1.5x / 2.0x), dispatched over Nostr or chunked via SMS.",
      "features.f8_title": "Smart OTP Extraction & Spam Filter",
      "features.f8_text": "Instant on-device detection and 1-tap copy of 2FA/banking SMS codes with top priority sorting and local spam isolation without internet access.",
      "features.f9_title": "Edge Console & Sovereign Updates",
      "features.f9_text": "Decentralized synchronization with Vercel Edge infrastructure (https://orbis-net.vercel.app) for emergency announcements, SHA-256 integrity verification, and opt-in diagnostics.",
      "features.f_guard_title": "Orbis Guard-LLM: Anti-Scam Shield",
      "features.f_guard_text": "Real-time vectorized semantic SMS analysis. Instant detection of fake banking URLs, smishing, and scams with tri-state classification (Safe, Suspicious, Danger) and 1-tap blocking.",
      "features.f_reply_title": "Orbis Reply-LLM: Smart Contextual Replies",
      "features.f_reply_text": "Instant on-device generation of 3 polite, relevant suggestion chips above the composer in encrypted chats and standard SMS across French, English, and Arabic.",
      "features.f_ai_engine_title": "100% Local CPU Vectorized AI Engine",
      "features.f_ai_engine_text": "Proprietary 100% native Kotlin neural models (< 2MB, < 10ms inference). Dynamic on-device continual training from user feedback with zero cloud servers and zero data leaks.",

      "security.tag": "FORTRESS ARCHITECTURE",
      "security.title": "Deep Multi-Layer Defense",
      "security.desc": "Every layer of OrbisNet is engineered to withstand censorship, internet blackouts, network wiretaps, and physical forensic extraction.",
      "security.s1_title": "BIP-340 Schnorr Signatures & secp256k1",
      "security.s1_text": "Native Nostr cryptographic authentication: every event and direct message is mathematically signed using your Schnorr private key, ensuring absolute tamper-proof authenticity.",
      "security.s2_title": "Double Ratchet & Perfect Forward Secrecy",
      "security.s2_text": "Automatic ratchet renewal of session keys. Past and future conversations remain unbreakable even if an ephemeral session key is compromised.",
      "security.s3_title": "At-Rest Storage via Android KeyStore TEE",
      "security.s3_text": "Local databases and private keys are sealed by a hardware AES-GCM master key managed in the processor's TEE secure enclave (anti-ADB dump).",
      "security.s4_title": "PBKDF2 100k Iterations Vault Derivation",
      "security.s4_text": "Your `.orbis` vault archives resist brute-force attacks thanks to slow PBKDF2 hashing and a 128-bit random salt.",
      "security.s5_title": "Encrypted Calls with SAS Verbal Code",
      "security.s5_text": "Direct AES-256-GCM audio/video streaming, verbal SAS verification against MITM interception, and guaranteed termination under 4.5s (Dead-Peer Watchdog).",
      "security.s6_title": "Digital Signatures & Anti-Tampering",
      "security.s6_text": "Every Nostr frame, SMS packet, and post is cryptographically signed. Package integrity is validated mathematically before execution to block tampering.",
      "security.s7_title": "Volatile RAM Zeroization & Zero-Trace",
      "security.s7_text": "Ephemeral ratchet keys and decrypted buffers are overwritten in RAM immediately after use (`fill(0)`), rendering cold-boot memory dump attacks ineffective.",
      "security.s8_title": "Real-Time Watchdog & Anti-Wiretap",
      "security.s8_text": "Active Dead-Peer Watchdog terminates sessions and purges crypto sockets after 4.5 seconds of network anomaly or silence, preventing hanging connections.",
      "security.s9_title": "100% Offline Vectorized AI Engine",
      "security.s9_text": "Both LLMs (Guard & Reply) run 100% locally on CPU via a native Kotlin matrix engine. Training data, private SMS text, and vector embeddings never leave your device.",
      "security.s10_title": "Hardware Benchmark & Granular Model Control",
      "security.s10_text": "On-device testing suite measuring sub-millisecond CPU latency (< 1ms), throughput, and memory footprint, with granular toggles disabled by default.",
      "security.s11_title": "Duress PIN & Anti-Coercion Decoy Profile",
      "security.s11_text": "Hardware-level panic defense: entering an alternate duress PIN unlocks a harmless decoy profile while silently purging or locking cryptographic session keys under physical coercion.",
      "security.s12_title": "Vercel Edge Telemetry & SHA-256 Verification",
      "security.s12_text": "Sovereign Edge backend (https://orbis-net.vercel.app) for broadcasting emergency announcements, verifying APK signatures via SHA-256, and zero-PII network diagnostics.",

      "help.tag": "OFFICIAL GUIDE & HELP CENTER",
      "help.title": "How Does OrbisNet Work?",
      "help.desc": "Discover the official guide to mastering Nostr hybridization, GSM cellular fallback, and sovereign security.",
      "help.search_placeholder": "Search topics (e.g. nostr, fallback, gsm, calls, keys, relays, ai)...",
      "help.cat_all": "All",
      "help.cat_start": "🚀 Getting Started",
      "help.cat_nostr": "⚡ Nostr & Relays",
      "help.cat_chat": "💬 Messaging",
      "help.cat_calls": "📞 E2EE Calls",
      "help.cat_ai": "🧠 AI & LLM",
      "help.cat_sec": "🔐 Security",
      "help.cat_sys": "⚙️ System & GSM",

      "topics.t_telegram_title": "How to get updates & join the community?",
      "topics.t_telegram_content": "Join our official Telegram community (t.me/orbis_community) or visit the website to download new signed APK releases directly, receive security alerts, and participate in community discussions and feedback.",
      "topics.t1_title": "How does the hybrid Nostr + GSM SMS architecture work?",
      "topics.t1_content": "When connected to the internet (Wi-Fi or mobile data), OrbisNet connects to global decentralized Nostr relays via WebSockets for instantaneous, zero-cost communication (Kind 1 notes, NIP-44 DMs). As soon as internet is unavailable, offline, or censored, the app seamlessly and automatically falls back to the cellular GSM SMS network with end-to-end encryption (AES-256-GCM + Double Ratchet).",
      "topics.t2_title": "What is the Nostr protocol and npub / nsec keys?",
      "topics.t2_content": "Nostr is a decentralized communication protocol with no central server or governing authority. Your account is composed of a cryptographic keypair: your public key (npub) acts as your shareable address, and your private key (nsec) stays on your device to sign your messages (BIP-340 Schnorr). No email address or phone number is required to use Nostr.",
      "topics.t3_title": "How does key pairing and security exchange work?",
      "topics.t3_content": "On first contact, peers exchange public keys via offline QR Code scan or automated secure exchange over Nostr relays or SMS. Once verified, an impenetrable end-to-end encrypted channel with Perfect Forward Secrecy is created.",
      "topics.t_call_e2ee_title": "How do E2EE encrypted voice and video calls work?",
      "topics.t_call_e2ee_content": "Call signaling uses a WebRTC frame over Nostr relays (online) or compact GSM SMS (offline). Audio/video streams directly peer-to-peer (P2P) encrypted in AES-256-GCM. A short SAS (Short Authentication String) code allows verbal verification against eavesdropping.",
      "topics.t_call_credit_title": "What happens if a user has no internet and 0 SMS credit during a call?",
      "topics.t_call_credit_content": "If internet is down and SMS balance is depleted, OrbisNet instantly detects the situation and offers fallback to a standard GSM cellular call. The receiving party can pick up for free according to their mobile carrier plan.",
      "topics.t_call_sas_title": "What is the SAS security code during a call?",
      "topics.t_call_sas_content": "The Short Authentication String (SAS) code is derived from session crypto fingerprints and shown on both screens. Reading this short code aloud mathematically confirms zero intermediary wiretapping (MITM) or rogue cellular towers.",
      "topics.t_call_hangup_title": "How is reliable hangup and call termination guaranteed?",
      "topics.t_call_hangup_content": "Upon ending a call, OrbisNet transmits an immediate teardown signal and activates a Dead-Peer watchdog. In case of network drop, the session terminates automatically after 4.5 seconds of silence to preserve battery and privacy.",
      "topics.t4_title": "Does GPS location sharing require mobile data?",
      "topics.t4_content": "No! Satellite GPS hardware works without mobile internet or data SIMs. OrbisNet extracts coordinates and transmits them in a single encrypted SMS (or via Nostr when online), opening maps directly on the recipient's phone.",
      "topics.t5_title": "How do social feeds, stories, and polls operate?",
      "topics.t5_content": "On Nostr, you can publish public notes (Kind 1), interactive polls, and ephemeral 24h stories visible across the decentralized network. You can also switch to 'Friends-Only' mode to restrict distribution strictly to trusted peer circles via encrypted P2P.",
      "topics.t6_title": "How does Steganography protect against censorship?",
      "topics.t6_content": "Steganography conceals encrypted payloads into zero-width invisible Unicode characters inside innocuous sentences, looking like normal SMS to carriers.",
      "topics.t7_title": "What is the actual data and SMS consumption?",
      "topics.t7_content": "Online (Nostr relays over Wi-Fi or 4G/5G), you consume 0 SMS and only a few kilobytes of data. In offline mode, standard text, reactions, or GPS shares consume only 1 single SMS. On unlimited SMS plans, the cost is completely zero.",
      "topics.t8_title": "Is OrbisNet available on iPhone (Apple iOS)?",
      "topics.t8_content": "No, OrbisNet is strictly exclusive to Android. Apple's closed iOS sandbox explicitly prohibits third-party apps from acting as the default SMS telephony client, intercepting raw GSM SMS frames in the background, or directly controlling cellular modem hardware. Only Android's open architecture enables true, decentralized off-grid digital sovereignty.",
      "topics.t_guard_title": "How does Orbis Guard-LLM detect scams and spam?",
      "topics.t_guard_content": "Orbis Guard-LLM analyzes inbound standard SMS using local vector embeddings. It spots urgency pressure tactics, spoofed bank warnings, fraudulent SIM alerts, and phishing domains (while correctly ignoring normal abbreviations like 'M.Pierre'). Suspicious texts are safely isolated in the 'Spam & Blocked' tab with risk warnings and 1-tap blocking.",
      "topics.t_reply_title": "How does Orbis Reply-LLM generate smart replies offline?",
      "topics.t_reply_content": "Orbis Reply-LLM analyzes conversation context and intent (greetings, questions, confirmations, urgency, gratitude) to generate 3 actionable suggestion chips above the keyboard. Inference executes in under 10ms with zero internet connection or background requests.",
      "topics.t_ai_learning_title": "How do the AI models train without compromising privacy?",
      "topics.t_ai_learning_content": "Model adaptation happens exclusively on your device (on-device continual learning). When you block a spam message or pick reply suggestions, local neural weights adapt dynamically within the app's encrypted storage. No text, vector weights, or statistics are ever transmitted to any remote server.",
      "topics.t_edge_title": "What is the purpose of the Vercel Edge console (https://orbis-net.vercel.app)?",
      "topics.t_edge_content": "The Edge infrastructure hosted on Vercel provides an ultra-fast, sovereign synchronization endpoint to verify APK update integrity (SHA-256 hash), broadcast critical emergency alerts, and monitor network health with zero personally identifiable information (zero PII).",
      "topics.t_legal_title": "Privacy Policy & Terms of Service",
      "topics.t_legal_content": "OrbisNet is a sovereign hybrid infrastructure. No personal data is collected or sent to third-party servers. All communications are end-to-end encrypted (AES-256-GCM / BIP-340 / Double Ratchet). As software provided 'as is', each user remains solely and legally responsible for their usage and compliance with local laws.",

      "quota.tag": "NETWORK TRANSPARENCY",
      "quota.title": "Nostr Network & SMS Quota Usage",
      "quota.desc": "OrbisNet optimizes every byte for zero-cost operation online and maximum frugality off-grid.",
      "quota.th_action": "Action Performed",
      "quota.th_cost": "Network Cost",
      "quota.th_details": "Technical Details",
      "quota.row1_title": "Text Message & DMs (Online Mode)",
      "quota.row1_cost": "0 SMS (Data < 1 KB)",
      "quota.row1_desc": "Decentralized Nostr WebSockets • NIP-44 / AES-GCM encryption • Instant",
      "quota.row_fallback_title": "1-on-1 Text Message (Offline GSM Mode)",
      "quota.row_fallback_cost": "1 SMS",
      "quota.row_fallback_desc": "AES-256-GCM + Double Ratchet PFS + ZLIB compression",
      "quota.row_call_title": "E2EE Voice & Video Call Signaling",
      "quota.row_call_cost": "0 SMS (Nostr) / 2 SMS (GSM)",
      "quota.row_call_desc": "Session key exchange • 0 SMS consumed during audio/video stream conversation",
      "quota.row2_title": "Satellite GPS Location Sharing",
      "quota.row2_cost": "0 SMS (Nostr) / 1 SMS (GSM)",
      "quota.row2_desc": "Ultra-compact coordinates ~40 bytes",
      "quota.row3_title": "Emoji Reaction & Delivery ACK",
      "quota.row3_cost": "0 SMS (Nostr) / 1 SMS (GSM)",
      "quota.row3_desc": "Nostr NIP-25 event or compact GSM ACK frame",
      "quota.row4_title": "Social Post & 24h Stories",
      "quota.row4_cost": "0 SMS (Nostr) / Direct P2P",
      "quota.row4_desc": "Broadcast over Nostr relays or direct encrypted P2P between circles",
      "quota.row5_title": "Voice Audio Note (3 to 5 seconds)",
      "quota.row5_cost": "0 SMS (Nostr) / 5 to 12 SMS (GSM)",
      "quota.row5_desc": "AMR/AAC audio compressed in multi-part fragments via SMS or instant online",
      "quota.row_ai_title": "AI Guard-LLM & Reply-LLM Analysis",
      "quota.row_ai_cost": "0 SMS • 0 KB Data",
      "quota.row_ai_desc": "100% On-Device CPU inference • 0 bytes sent or received • Zero telemetry",

      "dl.title": "Download OrbisNet Today",
      "dl.desc": "Reclaim control of your communications. Download the sovereign hybrid app directly without centralized stores.",
      "dl.btn_apk": "Download Official APK",
      "dl.qr_badge": "Quick Scan",
      "dl.qr_title": "Direct Smartphone Install",
      "dl.qr_desc": "Scan this QR Code with your Android phone camera to start instant download.",

      "legal.modal_title": "Privacy & Terms of Service",
      "legal.badge": "Sovereign • Hybrid Nostr & GSM",
      "legal.tab_privacy": "Privacy Policy",
      "legal.tab_terms": "Terms of Use",
      "legal.p1_title": "1. Decentralized Architecture & Zero Profiling",
      "legal.p1_desc": "OrbisNet has no central servers for tracking or profiling. Communications flow over decentralized Nostr relays or directly peer-to-peer over the cellular GSM network.",
      "legal.p2_title": "2. Hardware-Backed End-to-End Encryption",
      "legal.p2_desc": "All private communications are encrypted with AES-256-GCM, Double Ratchet (PFS), BIP-340 Schnorr signatures, and secp256k1 keys sealed in Android KeyStore TEE hardware.",
      "legal.p3_title": "3. Exclusively Local & Sovereign Storage",
      "legal.p3_desc": "Your chat history, private keys, and archives are kept strictly on your device. You can export a PBKDF2-encrypted backup or wipe everything at any time.",
      "legal.p4_title": "4. Nostr Network & Cellular Metadata",
      "legal.p4_desc": "On Nostr, public posts are broadcast to chosen WebSocket relays. In GSM fallback mode, encrypted SMS packets transit via your mobile carrier, subject to telecom routing logs.",
      "legal.t1_title": "1. Personal Use License & 'As-Is' Provision",
      "legal.t1_desc": "OrbisNet is sovereign software distributed 'as-is', without warranty of any kind, express or implied, including uninterrupted availability or fitness for a particular purpose.",
      "legal.t2_title": "2. User Legal Responsibility & Compliance",
      "legal.t2_desc": "You acknowledge that you are solely responsible for your use of the application, keeping your private keys safe, and complying with all applicable laws in your jurisdiction.",
      "legal.t3_title": "3. Developer Limitation of Liability",
      "legal.t3_desc": "The creators, developers, and contributors of OrbisNet expressly disclaim any liability for any damages or unlawful activities resulting from the misuse of this software.",
      "legal.t4_title": "4. Emergency Features (SOS & GPS)",
      "legal.t4_desc": "GPS location sharing and SOS alert features are peer-to-peer assistance tools based on local radio signals and do not replace official public emergency services.",
      "legal.btn_close": "Understood & Accept",
      "legal.open_modal_btn": "View Terms & Privacy Policy",

      "footer.privacy": "Privacy Policy",
      "footer.terms": "Terms of Use",
      "footer.tagline": "Sovereign hybrid Nostr & GSM messenger and social network powered by ShaDevPro."
    },

    ar: {
      "nav.features": "المميزات",
      "nav.security": "الأمان والتشفير",
      "nav.help": "مركز المساعدة",
      "nav.quota": "استهلاك الشبكة",
      "nav.download": "تحميل التطبيق",
      "nav.get_app": "تحميل APK",

      "hero.pill": "بروتوكول هجين لامركزي • ترحيل Nostr ورسائل GSM بدون إنترنت",
      "hero.title_part1": "الإنترنت الحر واللامركزي.",
      "hero.title_part2": "متصل عبر Nostr. وبدون إنترنت عبر GSM.",
      "hero.subtitle": "أول منصة سيادية في العالم تدمج شبكة Nostr اللامركزية (WebSocket) مع بديل رسائل GSM SMS المباشرة عند انقطاع الإنترنت. تشفير عسكري AES-256-GCM، بروتوكول Double Ratchet، تواقيع Schnorr BIP-340، وذكاء اصطناعي محلي 100%.",
      "hero.download_btn": "تحميل OrbisNet v1.1.0",
      "hero.guide_btn": "دليل الاستخدام والأسئلة الشائعة",
      "hero.telegram_btn": "مجتمع تيليجرام الرسمي",
      "dl.btn_telegram": "مجتمع تيليجرام",
      "dl.btn_github": "مستودع الموقع والإصدارات",
      "dl.checksum_title": "البصمة الرقمية للتحقق",
      "dl.copy_btn": "نسخ",
      "dl.copied_btn": "✓ تم النسخ!",

      "badges.encryption_title": "BIP-340 و Double Ratchet",
      "badges.encryption_sub": "توقيع Schnorr وتشفير AES-256",
      "badges.offline_title": "هجين Nostr + GSM",
      "badges.offline_sub": "ترحيل الويب وبديل SMS تلقائي",
      "badges.hardware_title": "حماية KeyStore TEE",
      "badges.hardware_sub": "حماية عتادية بالمعالج",

      "mockup.peer_name": "نظير Nostr • npub1...",
      "mockup.status": "● متصل • ترحيل Nostr + شبكة GSM جاهزة",
      "mockup.msg1": "⚡ متصل بترحيل لامركزي wss://relay.damus.io. تواقيع Schnorr BIP-340 نشطة.",
      "mockup.msg2": "ممتاز! في حال انقطاع الإنترنت، يتم التبديل التلقائي الفوري لرسائل GSM SMS المشفرة.",
      "mockup.gps": "🗺️ GPS: 33.5731° N, 7.5898° W",
      "mockup.msg3": "مكالمات E2EE جاهزة • درع Guard-LLM نشط 🛡️",
      "mockup.placeholder": "رسالة مشفرة Nostr / SMS...",

      "features.tag": "البنية الهجينة",
      "features.title": "السيادة الرقمية المتكاملة",
      "features.desc": "قوة شبكة عالمية لامركزية (Nostr) مدمجة مع صلابة بروتوكول خلوي مباشر بدون إنترنت (GSM SMS).",
      "features.f1_title": "مراسلة هجينة عبر Nostr ورسائل P2P",
      "features.f1_text": "مراسلة فورية عبر خوادم ترحيل Nostr اللامركزية عبر الإنترنت (رسائل خاصة NIP-04/NIP-44)، مع تحويل تلقائي سلس إلى رسائل GSM SMS المشفرة (Double Ratchet PFS) عند انقطاع الإنترنت.",
      "features.f2_title": "هوية Nostr وتواقيع Schnorr BIP-340",
      "features.f2_text": "هوية تشفيرية سيادية (مفاتيح npub/nsec). لا حاجة لبريد إلكتروني أو رقم هاتف للوصول إلى Nostr. يتم توقيع كل منشور ورسالة حسابياً عبر Schnorr (secp256k1).",
      "features.f3_title": "مكالمات صوت وفيديو مشفرة E2EE",
      "features.f3_text": "مكالمات WebRTC مشفرة طرفاً لطرف مع إشارات عبر Nostr أو SMS عند انقطاع النت، بث مباشر، توثيق برمز SAS لمكافحة التنصت، وتحويل فوري لمكالمات GSM الخلوية.",
      "features.f4_title": "موجز اجتماعي عالمي وقصص 24 ساعة",
      "features.f4_text": "انشر ملاحظات عامة (Kind 1)، استطلاعات رأي تفاعلية، وقصصاً مؤقتة على شبكة Nostr العالمية، أو قصر النشر حصرياً على دوائر أصدقائك الموثوقين بتشفير P2P.",
      "features.f5_title": "إخفاء البيانات غير المرئي (Steganography)",
      "features.f5_text": "في وضع الطوارئ عبر الرسائل القصيرة، يتم إخفاء البيانات المشفرة كأحرف يونيكود خفية عديمة العرض داخل جمل عادية لتجاوز الرقابة.",
      "features.f6_title": "خزنة مشفرة V3 وترحيل مباشر",
      "features.f6_text": "نسخ احتياطي فائق الأمان عبر PBKDF2 (100 ألف دورة)، تنظيف تلقائي للمساحة وترحيل البيانات بين الهواتف دون أي اعتماد على سحابة مركزية.",
      "features.f7_title": "رسائل صوتية ومخطط تفاعلي",
      "features.f7_text": "ضغط فائق ZLIB، تصفح دقيق باللمس مع خيارات سرعة تشغيل ديناميكية (1.0x / 1.5x / 2.0x)، تُرسل فوراً عبر Nostr أو مقسمة عبر SMS.",
      "features.f8_title": "استخراج رموز OTP وتصفية ذكية",
      "features.f8_text": "اكتشاف فوري ونسخ بنقرة واحدة لرموز التحقق الثنائي والبنوك مع أولوية في أعلى القائمة وتصفية محلية للرسائل المزعجة بدون إنترنت.",
      "features.f9_title": "لوحة تحكم Edge وتحديثات سيادية",
      "features.f9_text": "مزامنة آمنة مع بنية Vercel Edge التحتية (https://orbis-net.vercel.app) لتلقي تنبيهات الطوارئ والتحقق من بصمة SHA-256 والتشخيصات المجهولة.",
      "features.f_guard_title": "Orbis Guard-LLM : درع مكافحة الاحتيال",
      "features.f_guard_text": "تحليل دلالي متجهي فوري لرسائل SMS. اكتشاف الروابط البنكية المزيفة والاحتيال وتصنيف ثلاثي (آمن، مشبوه، خطير) مع حظر فوري بنقرة واحدة.",
      "features.f_reply_title": "Orbis Reply-LLM : اقتراحات الرد الذكية",
      "features.f_reply_text": "توليد فوري لـ 3 ردود سريعة ومهذبة ومطابقة للسياق أعلى لوحة الكتابة في المحادثات المشفرة والرسائل العادية باللغات الفرنسية والإنجليزية والعربية.",
      "features.f_ai_engine_title": "محرك ذكاء اصطناعي متجهي محلي 100%",
      "features.f_ai_engine_text": "نماذج عصبية سيادية بلغة كوتلن الأصلية 100% (أقل من 2 ميغابايت وسرعة استنتاج أقل من 10 مللي ثانية). تعلّم مستمر محلياً من تفاعلات المستخدم بدون أي خوادم خارجية أو تسريب بيانات.",

      "security.tag": "بنية الحصن الرقمي",
      "security.title": "أمان متعدد الطبقات في العمق",
      "security.desc": "تم تصميم كل طبقة في OrbisNet لمقاومة الرقابة، انقطاع الإنترنت، التنصت على الشبكات، واستخراج البيانات المادي.",
      "security.s1_title": "تواقيع Schnorr BIP-340 ومنحنى secp256k1",
      "security.s1_text": "توثيق تشفيري أصلي في Nostr: يتم توقيع كل حدث ورسالة خاصة حسابياً بمفتاح Schnorr الخاص بك، مما يضمن سلامة مطلقة للبيانات.",
      "security.s2_title": "بروتوكول Double Ratchet وسرية مستقبلية",
      "security.s2_text": "تجديد تلقائي وغير قابل للعكس لمفاتيح الجلسة، مما يجعل المحادثات السابقة والمستقبلية غير قابلة للاختراق حتى في حال كشف مفتاح مؤقت.",
      "security.s3_title": "تخزين محمي عبر Android KeyStore TEE",
      "security.s3_text": "قواعد البيانات المحلية والمفاتيح الخاصة مشفرة بمفتاح عتادي AES-GCM داخل بيئة المعالج الآمنة TEE لمقاومة أي سحب عبر ADB.",
      "security.s4_title": "اشتقاق الخزنة عبر PBKDF2 بـ 100 ألف دورة",
      "security.s4_text": "حماية النسخ الاحتياطية ضد هجمات التخمين بفضل 100 ألف تكرار هاش PBKDF2 وملح عشوائي 128 بت.",
      "security.s5_title": "مكالمات مشفرة مع رمز التحقق الشفهي SAS",
      "security.s5_text": "بث صوت وفيديو مشفر بـ AES-256-GCM، مطابقة شفهية لرمز SAS لمنع التنصت وضمان إنهاء المكالمة خلال 4.5 ثوانٍ.",
      "security.s6_title": "التوقيع الرقمي ومكافحة التلاعب",
      "security.s6_text": "يتم توقيع كل حزمة ورسالة Nostr أو SMS رقمياً، مع التحقق الحسابي من سلامة البيانات قبل التنفيذ لمنع أي تزوير.",
      "security.s7_title": "إتلاف الذاكرة المؤقتة وانعدام الأثر",
      "security.s7_text": "تدمير فوري للمفاتيح المؤقتة والبيانات المفكوكة في ذاكرة RAM بمجرد قراءتها (`fill(0)`) للحماية ضد هجمات تفريغ الذاكرة والتحليل الجنائي.",
      "security.s8_title": "حارس الجلسات وقطع الاتصال الفوري",
      "security.s8_text": "مراقب آلي نشط يغلق جلسات الاتصال ويدمر القنوات المشفرة بعد 4.5 ثوانٍ من انقطاع الإشارة لمنع أي ثغرات أو تنصت غير مباشر.",
      "security.s9_title": "محرك ذكاء اصطناعي متجهي بدون إنترنت",
      "security.s9_text": "يعمل كِلا النموذجين (Guard و Reply) بنسبة 100% محلياً على المعالج عبر محرك كوتلن أصلي دون إرسال أي بيانات عبر الشبكة.",
      "security.s10_title": "منصة اختبار العتاد والتحكم الدقيق بالنماذج",
      "security.s10_text": "أداة مدمجة لقياس زمن استجابة المعالج (< 1 جزء من الألف من الثانية)، سرعة المعالجة واستهلاك الذاكرة، مع خيار التعطيل الافتراضي.",
      "security.s11_title": "رمز PIN للطوارئ والملف المموه لمقاومة الإكراه",
      "security.s11_text": "دفاع استباقي ضد الإكراه الجسدي: إدخال رمز بديل يفتح ملفاً وهمياً فارغاً فورياً ويقوم بتطهير أو قفل مفاتيح التشفير في صمت لحماية خصوصيتك.",
      "security.s12_title": "بنية Vercel Edge والتحقق من بصمة SHA-256",
      "security.s12_text": "واجهة Edge سيادية (https://orbis-net.vercel.app) لبث إعلانات الطوارئ والتحقق من بصمات APK عبر SHA-256 والتشخيصات المجهولة تماماً.",

      "help.tag": "الدليل الرسمي ومركز المعرفة",
      "help.title": "كيف يعمل تطبيق OrbisNet؟",
      "help.desc": "اكتشف الدليل الرسمي لإتقان التكامل بين Nostr والبديل الخلوي GSM والأمان السيادي.",
      "help.search_placeholder": "ابحث في المواضيع (مثال: nostr، بدون نت، مكالمات، مفاتيح، ترحيل، ذكاء اصطناعي)...",
      "help.cat_all": "الكل",
      "help.cat_start": "🚀 البداية",
      "help.cat_nostr": "⚡ شبكة Nostr",
      "help.cat_chat": "💬 المراسلة",
      "help.cat_calls": "📞 المكالمات",
      "help.cat_ai": "🧠 الذكاء الاصطناعي",
      "help.cat_sec": "🔐 الأمان",
      "help.cat_sys": "⚙️ النظام و GSM",

      "topics.t_telegram_title": "كيفية الحصول على التحديثات والانضمام إلى المجتمع؟",
      "topics.t_telegram_content": "انضم إلى مجتمعنا الرسمي على تيليجرام (t.me/orbis_community) أو تصفح الموقع الرسمي لتحميل إصدارات APK الموقعة مباشرة، وتلقي تنبيهات الأمان والمشاركة في النقاشات.",
      "topics.t1_title": "كيف تعمل بنية Nostr + GSM SMS الهجينة؟",
      "topics.t1_content": "عند توفر اتصال بالإنترنت (Wi-Fi أو بيانات خلوية)، يتصل OrbisNet بخوادم ترحيل Nostr اللامركزية العالمية عبر WebSockets لمحادثات فورية ومجانية بالكامل. وفور انقطاع الإنترنت أو حجبه، يتحول التطبيق تلقائياً وبشكل فوري لشبكة GSM SMS المشفرة طرفاً لطرف (AES-256-GCM و Double Ratchet).",
      "topics.t2_title": "ما هو بروتوكول Nostr وما هي مفاتيح npub و nsec؟",
      "topics.t2_content": "Nostr هو بروتوكول تواصل لامركزي لا يخضع لأي سلطة مركزية. يتكون حسابك من زوج مفاتيح تشفير: المفتاح العام (npub) كعنوان للمشاركة، والمفتاح الخاص (nsec) يبقى على هاتفك لتوقيع رسائلك عبر تواقيع Schnorr BIP-340. لا تحتاج لأي بريد إلكتروني أو رقم هاتف لإنشاء حساب Nostr.",
      "topics.t3_title": "كيف يتم تبادل مفاتيح الأمان والاقتران؟",
      "topics.t3_content": "عند أول تواصل، يتبادل المستخدمون المفاتيح العامة إما بمسح رمز QR المباشر بدون شبكة، أو تلقائياً وبأمان عبر Nostr أو SMS. بمجرد التوثيق، يتم فتح قناة مشفرة ومحمية بسريّة مستقبلية تامة.",
      "topics.t_call_e2ee_title": "كيف تعمل مكالمات الصوت والفيديو المشفرة (E2EE)؟",
      "topics.t_call_e2ee_content": "تستخدم المكالمات إشارات WebRTC عبر ترحيل Nostr (أونلاين) أو رسائل SMS مصغرة (أوفلاين). يتم بث الصوت والفيديو بنظام الند للند مشفراً بـ AES-256-GCM. ويتيح رمز SAS القصير التحقق الشفهي لمنع أي تنصت.",
      "topics.t_call_credit_title": "ماذا يحدث عند انعدام الإنترنت ورصيد الرسائل أثناء الاتصال؟",
      "topics.t_call_credit_content": "إذا انقطع الإنترنت ونفد رصيد SMS، يكتشف OrbisNet الوضع فورياً ويعرض التحويل لمكالمة هاتفية GSM عادية. ويمكن للطرف المستلم الرد مجاناً وفق باقة هاتفه المعتادة.",
      "topics.t_call_sas_title": "ما هو رمز الأمان SAS أثناء المكالمة؟",
      "topics.t_call_sas_content": "رمز SAS هو كود مشتق يظهر على كلا الشاشتين. بمجرد قراءته صوتياً مع الطرف الآخر، يتأكد الطرفان رياضياً من استحالة وجود أي وسيط متنصت أو أبراج خلوية مزيفة.",
      "topics.t_call_hangup_title": "كيف يعمل إنهاء المكالمة وضمان إغلاق الخط؟",
      "topics.t_call_hangup_content": "عند إنهاء المكالمة، يرسل التطبيق إشارة إغلاق فورية ويفعل مراقب الاتصال. في حال انقطاع التغطية، يتم إنهاء الجلسة تلقائياً خلال 4.5 ثوانٍ لحماية البطارية والخصوصية.",
      "topics.t4_title": "هل تتطلب مشاركة الموقع GPS بيانات الهاتف؟",
      "topics.t4_content": "كلا! تعمل شريحة الأقمار الصناعية GPS بدون أي إنترنت. يقرأ التطبيق الإحداثيات ويرسلها مشفرة في رسالة قصيرة واحدة فقط (أو عبر Nostr عند الاتصال) لتفتح الخريطة مباشرة عند الطرف الآخر.",
      "topics.t5_title": "كيف تعمل الشبكة الاجتماعية والقصص واستطلاعات الرأي؟",
      "topics.t5_content": "على شبكة Nostr، يمكنك نشر ملاحظات عامة (Kind 1) واستطلاعات رأي تفاعلية وقصصاً مؤقتة لجميع النظراء، أو التبديل لوضع 'بين الأصدقاء' لحصر النشر في دوائر الثقة المقربة بتشفير P2P.",
      "topics.t6_title": "كيف تحمي تقنية الإخفاء (Steganography) من الرقابة؟",
      "topics.t6_content": "تقوم التقنية بإخفاء الحزمة المشفرة داخل جملة عادية جداً كأحرف غير مرئية. تبدو الرسالة لشركات الاتصالات أو المراقبين كحديث يومي عادي.",
      "topics.t7_title": "كم يستهلك التطبيق من البيانات ورصيد الرسائل القصيرة؟",
      "topics.t7_content": "أثناء الاتصال بالإنترنت (Nostr عبر Wi-Fi أو 4G/5G)، لا تستهلك أي رسالة (0 SMS) وكمية بيانات ضئيلة جداً (< 1 كيلوبايت). وفي وضع عدم الاتصال، تستهلك الرسائل أو مشاركة الموقع رسالة SMS واحدة فقط.",
      "topics.t8_title": "هل يتوفر تطبيق OrbisNet على هواتف آيفون (Apple iOS)؟",
      "topics.t8_content": "كلا، تطبيق OrbisNet حصري تماماً لنظام أندرويد. يمنع نظام آبل المغلق (iOS Sandbox) التطبيقات من التقاط رسائل GSM بالخلفية أو التحكم بالمودم الخلوي أو العمل كمعالج SMS افتراضي. نظام أندرويد المفتوح هو الوحيد القادر على دعم السيادة الرقمية الهجينة.",
      "topics.t_guard_title": "كيف يحمي Orbis Guard-LLM من رسائل الاحتيال والسبام؟",
      "topics.t_guard_content": "يفحص Orbis Guard-LLM كل رسالة واردة محلياً عبر متجهات سياقية. يكتشف أساليب الضغط والاستعجال والتنبيهات البنكية المزيفة ويعزل الروابط الخبيثة مع تصنيف ثلاثي (آمن، مشبوه، خطير) وخيار حظر فوري.",
      "topics.t_reply_title": "كيف يولد Orbis Reply-LLM ردوداً ذكية بدون إنترنت؟",
      "topics.t_reply_content": "يحلل Orbis Reply-LLM سياق المحادثة ومقصد الرسالة الأخيرة ليقدم 3 اقتراحات ردود سريعة ومهذبة أعلى لوحة المفاتيح. تتم المعالجة خلال أقل من 10 مللي ثانية دون أي اتصال بالإنترنت.",
      "topics.t_ai_learning_title": "كيف تتعلم النماذج ذاتياً دون انتهاك الخصوصية؟",
      "topics.t_ai_learning_content": "يتم التدريب والتحديث حصرياً على جهازك (on-device continual learning). عند حظر رسالة مزعجة أو اختيار ردود مقترحة، تتكيف الأوزان العصبية محلياً داخل وحدة التخزين المشفرة للتطبيق دون إرسال أي بيانات خارج هاتفك.",
      "topics.t_edge_title": "ما هي وظيفة لوحة تحكم Edge Vercel (https://orbis-net.vercel.app)؟",
      "topics.t_edge_content": "توفر بنية Edge المستضافة على Vercel نقطة مزامنة سيادية فائقة السرعة للتحقق من سلامة تحديثات APK (بصمة SHA-256) وبث إعلانات الطوارئ ومراقبة صحة الشبكة دون أي جمع لبيانات الهوية الشخصية.",
      "topics.t_legal_title": "سياسة الخصوصية والشروط وإخلاء المسؤولية",
      "topics.t_legal_content": "OrbisNet بنية تحتية هجينة سيادية. لا يتم جمع أو إرسال أي بيانات إلى أطراف ثالثة. يتم تشفير الاتصالات طرفاً لطرف (AES-256-GCM / BIP-340 / Double Ratchet). وبما أنه برنامج يُقدَّم \"كما هو\"، يتحمل كل مستخدم وحده المسؤولية القانونية الكاملة عن استخداماته.",

      "quota.tag": "شفافية استهلاك الشبكة",
      "quota.title": "استهلاك شبكة Nostr وباقة SMS",
      "quota.desc": "يحسن OrbisNet كل بايت لتقديم مجانية تامة عبر الإنترنت واستهلاك فائق التوفير بدون إنترنت.",
      "quota.th_action": "العملية",
      "quota.th_cost": "تكلفة الشبكة",
      "quota.th_details": "التفاصيل التقنية",
      "quota.row1_title": "رسالة نصية ورسائل خاصة (أونلاين)",
      "quota.row1_cost": "0 SMS (بيانات < 1 ك.ب)",
      "quota.row1_desc": "ترحيل WebSockets لامركزي عبر Nostr • تشفير NIP-44 / AES-GCM • فوري",
      "quota.row_fallback_title": "رسالة نصية 1 إلى 1 (أوفلاين GSM)",
      "quota.row_fallback_cost": "1 SMS",
      "quota.row_fallback_desc": "تشفير AES-256-GCM + بروتوكول Double Ratchet PFS + ضغط ZLIB",
      "quota.row_call_title": "إشارات مكالمات الصوت والفيديو E2EE",
      "quota.row_call_cost": "0 SMS (Nostr) / 2 SMS (GSM)",
      "quota.row_call_desc": "تبادل مفاتيح الجلسة • صفر رسائل مستهلكة أثناء البث الصوتي أو المرئي",
      "quota.row2_title": "مشاركة موقع GPS الفضائي",
      "quota.row2_cost": "0 SMS (Nostr) / 1 SMS (GSM)",
      "quota.row2_desc": "إحداثيات جغرافية فائقة الصغر ~40 بايت",
      "quota.row3_title": "تفاعلات الإيموجي وإشعارات الاستلام",
      "quota.row3_cost": "0 SMS (Nostr) / 1 SMS (GSM)",
      "quota.row3_desc": "حدث Nostr NIP-25 أو حزمة إشعار استلام GSM مصغرة",
      "quota.row4_title": "المنشورات الاجتماعية وقصص 24 ساعة",
      "quota.row4_cost": "0 SMS (Nostr) / P2P مباشر",
      "quota.row4_desc": "نشر عبر خوادم ترحيل Nostr أو اتصال P2P مباشر بين الدوائر الموثوقة",
      "quota.row5_title": "رسالة صوتية (3 إلى 5 ثوانٍ)",
      "quota.row5_cost": "0 SMS (Nostr) / 5 إلى 12 SMS (GSM)",
      "quota.row5_desc": "ملف صوتي AMR/AAC مضغوط ZLIB • فوري عبر النت أو مقسم عبر SMS",
      "quota.row_ai_title": "درع Guard-LLM واقتراحات Reply-LLM",
      "quota.row_ai_cost": "0 SMS • 0 ك.ب بيانات",
      "quota.row_ai_desc": "معالجة محلية 100% على معالج الهاتف • صفر بايت مرسل أو مستقبل • انعدام تام لأي تتبع",

      "dl.title": "حمّل تطبيق OrbisNet الآن",
      "dl.desc": "استعد السيطرة الكاملة على محادثاتك. حمّل التطبيق السيادي الهجين مباشرة بدون متاجر مركزية.",
      "dl.btn_apk": "تحميل ملف APK الرسمي",
      "dl.qr_badge": "مسح سريع",
      "dl.qr_title": "تثبيت مباشر على الهاتف",
      "dl.qr_desc": "امسح رمز الاستجابة السريعة بكاميرا هاتفك لبدء التحميل الفوري.",

      "legal.modal_title": "الخصوصية والشروط القانونية",
      "legal.badge": "سيادي • هجين Nostr و GSM",
      "legal.tab_privacy": "الخصوصية",
      "legal.tab_terms": "الشروط والأحكام",
      "legal.p1_title": "1. بنية لامركزية وانعدام المراقبة",
      "legal.p1_desc": "لا يملك OrbisNet أي خوادم مركزية للمراقبة أو التتبع. تنتقل البيانات عبر خوادم ترحيل Nostr اللامركزية أو مباشرة بين الهواتف عبر شبكة GSM الخلوية.",
      "legal.p2_title": "2. تشفير شامل طرفاً لطرف معتمد عتادياً",
      "legal.p2_desc": "جميع المراسلات الخاصة مشفرة بتقنيات AES-256-GCM و Double Ratchet وتواقيع Schnorr BIP-340 المحمية عتادياً في KeyStore TEE.",
      "legal.p3_title": "3. تخزين محلي حصري وسيادي على الجهاز",
      "legal.p3_desc": "يتم تخزين سجل المحادثات والمفاتيح السرية محلياً في هاتفك فقط. يمكنك تصدير نسخة احتياطية مشفرة بـ PBKDF2 أو مسح بياناتك في أي وقت.",
      "legal.p4_title": "4. شبكة Nostr وبيانات الإرسال الخلوية",
      "legal.p4_desc": "في Nostr، تُبث المنشورات العامة لخوادم الترحيل المختارة. وفي وضع طوارئ GSM، تنتقل الرسائل المشفرة عبر مشغل الهاتف وتخضع لسجلات التوجيه المعتادة.",
      "legal.t1_title": "1. رخصة الاستخدام وتوفير البرنامج 'كما هو'",
      "legal.t1_desc": "OrbisNet برنامج سيادي يُقدم 'كما هو' دون أي ضمانات صريحة أو ضمنية بشأن استمرارية الخدمة أو الملاءمة لغرض معين.",
      "legal.t2_title": "2. المسؤولية القانونية الحصرية للمستخدم",
      "legal.t2_desc": "تقر بأنك المسؤول الوحيد عن استخدامك للتطبيق والحفاظ على أمان مفاتيحك الخاصة (nsec) والالتزام بالقوانين المعمول بها في منطقتك.",
      "legal.t3_title": "3. إخلاء مسؤولية المطورين والمساهمين",
      "legal.t3_desc": "يخلي مطورو ومساهمو OrbisNet مسؤوليتهم المدنية والجنائية تماماً عن أي أضرار أو أنشطة غير قانونية ناتجة عن إساءة استخدام التطبيق.",
      "legal.t4_title": "4. ميزات الطوارئ (SOS وتحديد الموقع)",
      "legal.t4_desc": "ميزات مشاركة الموقع وتنبيه SOS هي أدوات مساعدة بين النظراء ولا تحل محل خدمات الطوارئ الرسمية في دولتك.",
      "legal.btn_close": "فهمت وأوافق",
      "legal.open_modal_btn": "عرض سياسة الخصوصية والشروط",

      "footer.privacy": "الخصوصية",
      "footer.terms": "الشروط والأحكام",
      "footer.tagline": "شبكة تواصل اجتماعي ومراسلة هجينة سيادية Nostr و GSM مطورة بواسطة ShaDevPro."
    }
  };

  // =========================================================================
  // 2. Language Switcher Logic & RTL Handling
  // =========================================================================
  let currentLang = 'fr';

  function applyLanguage(lang) {
    if (!translations[lang]) return;
    currentLang = lang;

    // Set HTML attribute & RTL
    document.documentElement.lang = lang;
    if (lang === 'ar') {
      document.documentElement.setAttribute('dir', 'rtl');
    } else {
      document.documentElement.setAttribute('dir', 'ltr');
    }

    // Update Text Elements
    document.querySelectorAll('[data-i18n]').forEach(el => {
      const key = el.getAttribute('data-i18n');
      if (translations[lang][key]) {
        el.textContent = translations[lang][key];
      }
    });

    // Update Placeholders
    document.querySelectorAll('[data-i18n-ph]').forEach(el => {
      const key = el.getAttribute('data-i18n-ph');
      if (translations[lang][key]) {
        el.setAttribute('placeholder', translations[lang][key]);
      }
    });

    // Update active button state
    document.querySelectorAll('.lang-btn').forEach(btn => {
      btn.classList.toggle('active', btn.getAttribute('data-lang') === lang);
    });

    localStorage.setItem('orbisnet_lang', lang);
  }

  // Bind Language Switcher Buttons
  document.querySelectorAll('.lang-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const lang = btn.getAttribute('data-lang');
      applyLanguage(lang);
    });
  });

  // Restore saved language preference
  const savedLang = localStorage.getItem('orbisnet_lang') || localStorage.getItem('orbis_lang') || 'fr';
  applyLanguage(savedLang);

  // =========================================================================
  // 3. Theme Toggle (Dark / Light)
  // =========================================================================
  const themeToggle = document.getElementById('themeToggle');
  const themeIcon = themeToggle?.querySelector('.theme-icon');

  function setTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    if (themeIcon) {
      themeIcon.textContent = theme === 'light' ? '🌙' : '☀️';
    }
    localStorage.setItem('orbisnet_theme', theme);
  }

  const savedTheme = localStorage.getItem('orbisnet_theme') || localStorage.getItem('orbis_theme') || 'light';
  setTheme(savedTheme);

  themeToggle?.addEventListener('click', () => {
    const currentTheme = document.documentElement.getAttribute('data-theme');
    const newTheme = currentTheme === 'light' ? 'dark' : 'light';
    setTheme(newTheme);
  });

  // =========================================================================
  // 4. Mobile Navigation Menu Toggle
  // =========================================================================
  const mobileToggle = document.getElementById('mobileToggle');
  const navMenu = document.getElementById('navMenu');

  mobileToggle?.addEventListener('click', () => {
    const isOpen = navMenu?.classList.toggle('open');
    mobileToggle.classList.toggle('active', isOpen);
  });

  document.querySelectorAll('.nav-link, .nav-mobile-ctas a').forEach(link => {
    link.addEventListener('click', () => {
      navMenu?.classList.remove('open');
      mobileToggle?.classList.remove('active');
    });
  });

  // =========================================================================
  // 5. Help Center Accordion & Filtering
  // =========================================================================
  const accordionItems = document.querySelectorAll('.accordion-item');
  const categoryPills = document.querySelectorAll('.category-pills .pill');
  const helpSearchInput = document.getElementById('helpSearch');

  // Accordion Toggle
  accordionItems.forEach(item => {
    const header = item.querySelector('.accordion-header');
    const body = item.querySelector('.accordion-body');

    header?.addEventListener('click', () => {
      const isOpen = item.classList.contains('active');

      // Close other accordions
      accordionItems.forEach(other => {
        other.classList.remove('active');
        const otherBody = other.querySelector('.accordion-body');
        if (otherBody) otherBody.style.maxHeight = null;
      });

      // Toggle current
      if (!isOpen && body) {
        item.classList.add('active');
        body.style.maxHeight = body.scrollHeight + 'px';
      }
    });
  });

  // Filter and Search Logic
  function filterHelpTopics() {
    const query = helpSearchInput?.value.toLowerCase().trim() || '';
    const activePill = document.querySelector('.category-pills .pill.active');
    const selectedCategory = activePill?.getAttribute('data-cat') || 'all';

    accordionItems.forEach(item => {
      const itemCat = item.getAttribute('data-category');
      const title = item.querySelector('.acc-title')?.textContent.toLowerCase() || '';
      const content = item.querySelector('.accordion-body p')?.textContent.toLowerCase() || '';

      const matchesCat = (selectedCategory === 'all') || (itemCat === selectedCategory);
      const matchesQuery = (query === '') || (title.includes(query) || content.includes(query));

      if (matchesCat && matchesQuery) {
        item.style.display = 'block';
      } else {
        item.style.display = 'none';
      }
    });
  }

  // Category Pill Click
  categoryPills.forEach(pill => {
    pill.addEventListener('click', () => {
      categoryPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      filterHelpTopics();
    });
  });

  // Search Input Event
  helpSearchInput?.addEventListener('input', filterHelpTopics);

  // =========================================================================
  // 6. Direct Download & QR Code Dynamic Configuration
  // =========================================================================
  const ORBISNET_CONFIG = {
    apkDownloadUrl: "https://github.com/orbisoffline-cloud/ORBIS/releases/download/ORBIS-v1.1.0/O.R.B.I.S.apk",
    githubRepoUrl: "https://github.com/orbisoffline-cloud/ORBIS",
    version: "1.1.0",
    build: 110,
    size: "4.64 Mo"
  };

  function updateDownloadUrls(url) {
    const qrImg = document.getElementById('apkQrCodeImg');
    const directBtn = document.getElementById('directDownloadBtn');
    if (qrImg) {
      qrImg.src = 'qr-code.svg';
    }
    if (directBtn) {
      directBtn.href = url;
    }
  }

  // Initialize with official release URL
  updateDownloadUrls(ORBISNET_CONFIG.apkDownloadUrl);

  const directDownloadBtn = document.getElementById('directDownloadBtn');
  directDownloadBtn?.addEventListener('click', () => {
    console.log('[OrbisNet] Download APK triggered:', ORBISNET_CONFIG.apkDownloadUrl);
  });

  // Checksum Copy-to-Clipboard
  const copyChecksumBtn = document.getElementById('copyChecksumBtn');
  const checksumValue = document.getElementById('checksumValue')?.textContent.trim();
  const copyChecksumText = document.getElementById('copyChecksumText');

  copyChecksumBtn?.addEventListener('click', async () => {
    if (!checksumValue) return;
    try {
      await navigator.clipboard.writeText(checksumValue);
      const currentLang = document.documentElement.getAttribute('lang') || 'fr';
      const copiedStr = translations[currentLang]?.['dl.copied_btn'] || '✓ Copié !';
      if (copyChecksumText) copyChecksumText.textContent = copiedStr;
      copyChecksumBtn.classList.add('copied');
      setTimeout(() => {
        const copyStr = translations[currentLang]?.['dl.copy_btn'] || 'Copier';
        if (copyChecksumText) copyChecksumText.textContent = copyStr;
        copyChecksumBtn.classList.remove('copied');
      }, 2000);
    } catch (e) {
      console.warn('[OrbisNet] Clipboard write failed:', e);
    }
  });

  // Open first accordion by default
  if (accordionItems.length > 0) {
    const firstBody = accordionItems[0].querySelector('.accordion-body');
    accordionItems[0].classList.add('active');
    if (firstBody) firstBody.style.maxHeight = firstBody.scrollHeight + 'px';
  }

  // =========================================================================
  // 7. Scroll Animations & Reading Progress
  // =========================================================================
  const scrollProgressBar = document.getElementById('scrollProgressBar');
  const backToTopBtn = document.getElementById('backToTopBtn');

  let isScrolling = false;
  window.addEventListener('scroll', () => {
    if (!isScrolling) {
      window.requestAnimationFrame(() => {
        const scrollTop = window.scrollY || document.documentElement.scrollTop;
        const scrollHeight = document.documentElement.scrollHeight - document.documentElement.clientHeight;
        const scrollPercent = scrollHeight > 0 ? (scrollTop / scrollHeight) * 100 : 0;

        if (scrollProgressBar) {
          scrollProgressBar.style.width = scrollPercent + '%';
        }

        if (backToTopBtn) {
          if (scrollTop > 380) {
            backToTopBtn.classList.add('visible');
          } else {
            backToTopBtn.classList.remove('visible');
          }
        }
        isScrolling = false;
      });
      isScrolling = true;
    }
  }, { passive: true });

  backToTopBtn?.addEventListener('click', () => {
    window.scrollTo({
      top: 0,
      behavior: 'smooth'
    });
  });

  // IntersectionObserver for Scroll Reveals
  const revealElements = document.querySelectorAll('.reveal, .reveal-scale');
  if ('IntersectionObserver' in window && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    const revealObserver = new IntersectionObserver((entries, observer) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-revealed');
          observer.unobserve(entry.target);
        }
      });
    }, {
      root: null,
      rootMargin: '0px 0px -40px 0px',
      threshold: 0.12
    });

    revealElements.forEach(el => revealObserver.observe(el));
  } else {
    revealElements.forEach(el => el.classList.add('is-revealed'));
  }

  // =========================================================================
  // 8. Legal & Privacy Terms Modal Controller
  // =========================================================================
  const legalModal = document.getElementById('legalModal');
  const closeLegalModalBtn = document.getElementById('closeLegalModalBtn');
  const acceptLegalModalBtn = document.getElementById('acceptLegalModalBtn');
  const legalTabBtns = document.querySelectorAll('.legal-tab-btn');
  const legalTabPrivacy = document.getElementById('legalTabPrivacy');
  const legalTabTerms = document.getElementById('legalTabTerms');

  function openLegalModal(tabIndex = 0) {
    if (!legalModal) return;
    switchLegalTab(tabIndex);
    legalModal.classList.add('open');
    legalModal.setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';
  }

  function closeLegalModal() {
    if (!legalModal) return;
    legalModal.classList.remove('open');
    legalModal.setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
  }

  function switchLegalTab(tabIndex) {
    const isPrivacy = tabIndex === 0 || tabIndex === '0';
    legalTabBtns.forEach(btn => {
      const target = btn.getAttribute('data-tab-target');
      btn.classList.toggle('active', isPrivacy ? (target === '0') : (target === '1'));
    });
    if (legalTabPrivacy) legalTabPrivacy.classList.toggle('active', isPrivacy);
    if (legalTabTerms) legalTabTerms.classList.toggle('active', !isPrivacy);
  }

  legalTabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const target = btn.getAttribute('data-tab-target');
      switchLegalTab(parseInt(target, 10));
    });
  });

  // Open modal triggers
  document.querySelectorAll('.open-legal-modal').forEach(trigger => {
    trigger.addEventListener('click', (e) => {
      e.preventDefault();
      const tab = trigger.getAttribute('data-tab') || '0';
      openLegalModal(parseInt(tab, 10));
    });
  });

  closeLegalModalBtn?.addEventListener('click', closeLegalModal);
  acceptLegalModalBtn?.addEventListener('click', closeLegalModal);

  // Click on background backdrop to close
  legalModal?.addEventListener('click', (e) => {
    if (e.target === legalModal || e.target.classList.contains('legal-modal-container')) {
      closeLegalModal();
    }
  });

  // Keyboard accessibility (ESC)
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && legalModal?.classList.contains('open')) {
      closeLegalModal();
    }
  });

  // Check URL hash for direct deep-linking (#privacy, #terms, #legal)
  function checkLegalHash() {
    const hash = window.location.hash.toLowerCase();
    if (hash === '#privacy') {
      openLegalModal(0);
    } else if (hash === '#terms' || hash === '#legal') {
      openLegalModal(1);
    }
  }

  checkLegalHash();
  window.addEventListener('hashchange', checkLegalHash);
});
