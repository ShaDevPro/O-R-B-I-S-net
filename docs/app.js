/**
 * OrbisNet — Sovereign Decentralized Nostr Network — Master Application Script
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
      "nav.quota": "Sobriété Réseau",
      "nav.download": "Télécharger",
      "nav.get_app": "Obtenir l'APK",

      "hero.pill": "Protocole Décentralisé Nostr • WebSockets & WebRTC E2EE pour Android",
      "hero.title_part1": "L'Internet Libre & Décentralisé.",
      "hero.title_part2": "Zéro Serveur Central. Zéro Censure.",
      "hero.subtitle": "La plateforme souveraine de communication propulsée par le réseau décentralisé Nostr (WebSockets) et les appels WebRTC chiffrés de bout en bout. Signatures Schnorr BIP-340, chiffrement NIP-44 et IA 100% locale.",
      "hero.download_btn": "Télécharger OrbisNet v1.1.0",
      "hero.guide_btn": "Explorer le Guide & FAQ",
      "hero.telegram_btn": "Communauté Telegram",
      "dl.btn_telegram": "Communauté Telegram",
      "dl.btn_github": "Dépôt Web & Releases",
      "dl.checksum_title": "Empreinte Cryptographique",
      "dl.copy_btn": "Copier",
      "dl.copied_btn": "✓ Copié !",

      "badges.encryption_title": "BIP-340 & NIP-44",
      "badges.encryption_sub": "Schnorr & Chiffrement E2EE",
      "badges.offline_title": "Relais Décentralisés",
      "badges.offline_sub": "WebSockets wss:// Ouverts",
      "badges.hardware_title": "Android KeyStore TEE",
      "badges.hardware_sub": "Enclave Matérielle Sécurisée",

      "mockup.peer_name": "Nostr Peer • npub1...",
      "mockup.status": "● Connecté • Relais Nostr (wss://)",
      "mockup.msg1": "⚡ Connecté au relais décentralisé wss://relay.damus.io. Signatures Schnorr BIP-340 actives.",
      "mockup.msg2": "Parfait ! Échanges privés chiffrés de bout en bout via NIP-44, zéro intermédiaire.",
      "mockup.gps": "🗺️ GPS: 33.5731° N, 7.5898° W",
      "mockup.msg3": "Appel WebRTC E2EE prêt • Bouclier IA Guard-LLM actif 🛡️",
      "mockup.placeholder": "Message chiffré Nostr...",

      "features.tag": "PILIERS SOUVERAINS",
      "features.title": "La Liberté Numérique Décentralisée",
      "features.desc": "Tout ce qu'un réseau social et une messagerie moderne offrent, propulsé par des relais décentralisés et un chiffrement mathématique absolu.",
      "features.f1_title": "Messagerie Décentralisée Nostr",
      "features.f1_text": "Échanges instantanés via les relais Nostr mondiaux en WebSockets. Chiffrement de bout en bout NIP-04 et NIP-44 garantissant la confidentialité absolue de vos messages privés.",
      "features.f2_title": "Identité Souveraine & Signatures BIP-340",
      "features.f2_text": "Identité cryptographique souveraine (paires de clés npub/nsec). Aucune adresse email ni numéro de téléphone requis. Chaque publication et message est signé numériquement par Schnorr (secp256k1).",
      "features.f3_title": "Appels Vocaux & Vidéo Chiffrés E2EE",
      "features.f3_text": "Appels WebRTC en pair à pair (P2P) haute fidélité chiffrés en AES-256-GCM. Validation du code de sécurité SAS contre les écoutes et zéro recours à la téléphonie classique.",
      "features.f4_title": "Flux Social Mondial Kind 1 & Stories 24h",
      "features.f4_text": "Publiez des notes publiques ouvertes sur le réseau mondial Nostr ou diffusez des stories éphémères et des sondages exclusivement vers vos cercles d'amis de confiance.",
      "features.f5_title": "Chiffrement Authentifié NIP-44",
      "features.f5_text": "Standard moderne de chiffrement de bout en bout pour Nostr : dérivation de clé par échange Diffie-Hellman secp256k1, scellement ChaCha20-Poly1305 et padding anti-analyse de taille.",
      "features.f6_title": "Coffre Chiffré V3 & Sauvegardes PBKDF2",
      "features.f6_text": "Sauvegarde intégrale dérivée par PBKDF2 (100 000 itérations), nettoyage intelligent du stockage et migration entre smartphones sans dépendance au cloud centralisé.",
      "features.f7_title": "Notes Vocales & Waveform Interactive",
      "features.f7_text": "Compression ZLIB/Deflater ultra-dense, scrubbing tactile fluide et sélecteur de vitesse de lecture (1.0x / 1.5x / 2.0x), acheminés instantanément sous forme de flux chiffré.",
      "features.f8_title": "Annuaire Souverain & Cercles de Confiance",
      "features.f8_text": "Gestionnaire de contacts décentralisé avec certification mutuelle par QR code, organisation par cercles d'affinité (Famille, Amis, Public) et filtres anti-spam locaux.",
      "features.f9_title": "Console Edge & Mises à Jour Souveraines",
      "features.f9_text": "Synchronisation décentralisée avec l'infrastructure Edge Vercel (https://orbis-net.vercel.app) pour les annonces d'urgence, la vérification d'intégrité SHA-256 et la supervision sans traçage.",
      "features.f_guard_title": "Orbis Guard-LLM : Bouclier Anti-Arnaque",
      "features.f_guard_text": "Analyse sémantique vectorisée en temps réel de vos messages. Détection des faux liens bancaires, phishing et arnaques avec classification tri-état (Sûr, Suspect, Danger) et blocage 1-clic.",
      "features.f_reply_title": "Orbis Reply-LLM : Suggestions Intelligentes",
      "features.f_reply_text": "Génération instantanée de 3 réponses rapides, polies et contextuelles au-dessus de votre saisie dans le chat en français, anglais et arabe.",
      "features.f_ai_engine_title": "Moteur IA Vectorisé 100% CPU Local",
      "features.f_ai_engine_text": "Modèles neuronaux propriétaires 100% Kotlin natif (< 2 Mo, inférence < 10 ms). Auto-apprentissage continu sur l'appareil à partir de vos retours, sans aucun serveur tiers ni fuite de données.",

      "security.tag": "ARCHITECTURE FORTERESSE",
      "security.title": "Sécurité Multi-Couches en Profondeur",
      "security.desc": "Chaque couche d'OrbisNet a été conçue pour résister à la censure, aux écoutes réseau et aux extractions physiques.",
      "security.s1_title": "Signatures Schnorr BIP-340 & secp256k1",
      "security.s1_text": "Authentification cryptographique native Nostr : chaque événement et message direct est signé numériquement par votre clé privée Schnorr, garantissant une intégrité mathématique absolue.",
      "security.s2_title": "Chiffrement Authentifié NIP-44 & PFS",
      "security.s2_text": "Chiffrement moderne de bout en bout des messages directs avec ChaCha20-Poly1305, dérivation de clé partagée et protection contre l'analyse de trafic par masquage de taille.",
      "security.s3_title": "Stockage au Repos via Android KeyStore TEE",
      "security.s3_text": "Les bases de données Room locales et clés privées sont scellées par une clé matérielle AES-GCM gérée dans la zone sécurisée TEE de votre processeur (anti-extraction ADB).",
      "security.s4_title": "Dérivation de Coffre PBKDF2 100k Itérations",
      "security.s4_text": "Vos archives de sauvegarde `.orbis` résistent aux attaques par force brute grâce au hachage ralenti PBKDF2 combiné à un sel aléatoire cryptographique de 128 bits.",
      "security.s5_title": "Appels Vocaux & Vidéo Chiffrés avec Code SAS",
      "security.s5_text": "Balisage audio/vidéo chiffré en AES-256-GCM via WebRTC direct, authentification vocale par code court SAS anti-interception et coupure garantie sous 4.5s (Dead-Peer Watchdog).",
      "security.s6_title": "Signature Numérique & Anti-Altération",
      "security.s6_text": "Chaque trame Nostr et publication est signée numériquement. L'intégrité du paquet est validée mathématiquement avant exécution pour bloquer toute falsification.",
      "security.s7_title": "Écrasement RAM & Zéro-Trace Volatile",
      "security.s7_text": "Dès leur lecture ou émission, les clés de session éphémères et fragments décodés sont écrasés en mémoire vive via `fill(0)` pour interdire toute extraction forensique.",
      "security.s8_title": "Watchdog Temps Réel & Rupture d'Écoute",
      "security.s8_text": "Un chien de garde actif coupe automatiquement les sessions d'appel après 4.5s de silence ou en cas d'anomalie réseau pour garantir l'étanchéité absolue.",
      "security.s9_title": "Moteur IA Vectorisé 100% Hors-Ligne",
      "security.s9_text": "Les deux LLM (Guard & Reply) s'exécutent 100% en local via un moteur matriciel Kotlin natif. Aucune donnée d'entraînement, discussion ou empreinte vectorielle ne transite sur le réseau.",
      "security.s10_title": "Banc de Test & Benchmark Matériel Dédié",
      "security.s10_text": "Outil de mesure temps réel de la latence CPU (< 1 ms), du débit d'inférence et de la RAM, avec activation granulaire désactivée par défaut pour un consentement souverain.",
      "security.s11_title": "Code PIN de Détresse & Profil Leurre Anti-Contrainte",
      "security.s11_text": "Système de défense physique (Duress Manager) : la saisie d'un code secret alternatif déverrouille un profil leurre tout en détruisant silencieusement les clés de chiffrement.",
      "security.s12_title": "Infrastructure Edge Vercel & Vérification SHA-256",
      "security.s12_text": "Point de synchronisation souverain (https://orbis-net.vercel.app) pour les alertes de sécurité d'urgence, la vérification d'intégrité des APK par SHA-256 et la supervision sans traçage.",

      "help.tag": "GUIDE OFFICIEL & CENTRE D'AIDE",
      "help.title": "Comment Fonctionne OrbisNet ?",
      "help.desc": "Découvrez le guide officiel pour maîtriser le réseau décentralisé Nostr, les appels WebRTC E2EE et la sécurité souveraine.",
      "help.search_placeholder": "Rechercher un sujet (ex: nostr, appels, clés, relais, ia, e2ee)...",
      "help.cat_all": "Tous",
      "help.cat_start": "🚀 Démarrage",
      "help.cat_nostr": "⚡ Nostr & Relais",
      "help.cat_chat": "💬 Messagerie",
      "help.cat_calls": "📞 Appels E2EE",
      "help.cat_ai": "🧠 IA & LLM",
      "help.cat_sec": "🔐 Sécurité",
      "help.cat_sys": "⚙️ Système & Réseau",

      "topics.t_telegram_title": "Comment recevoir les mises à jour et rejoindre la communauté ?",
      "topics.t_telegram_content": "Rejoignez notre communauté Telegram officielle (t.me/orbis_community) ou consultez le site web pour télécharger les nouvelles versions APK signées en direct, recevoir les alertes de sécurité et participer aux discussions et retours d'expérience.",
      "topics.t1_title": "Comment OrbisNet fonctionne-t-il sans serveur central ?",
      "topics.t1_content": "OrbisNet s'appuie sur le protocole décentralisé Nostr. L'application se connecte directement via WebSockets sécurisés (wss://) à un maillage mondial de relais indépendants sur Wi-Fi ou Données Mobiles (4G/5G). Aucun serveur unique ne contrôle vos données, éliminant tout risque de fermeture ou de censure centrale.",
      "topics.t2_title": "Qu'est-ce que le protocole Nostr et les clés npub / nsec ?",
      "topics.t2_content": "Nostr est un protocole de communication décentralisé, sans serveur central ni autorité de contrôle. Votre compte est constitué d'une paire de clés cryptographiques : votre clé publique (npub) qui sert d'adresse partagée, et votre clé privée (nsec) qui reste sur votre appareil pour signer vos messages (BIP-340 Schnorr). Aucun email ni numéro de téléphone n'est requis pour créer ou utiliser un compte Nostr.",
      "topics.t3_title": "Comment fonctionne l'appairage et l'échange de clés de sécurité ?",
      "topics.t3_content": "Lors de la première prise de contact, les utilisateurs s'échangent leurs clés de chiffrement soit par scan de QR Code direct (instantané), soit par échange automatique sécurisé via relais Nostr. Une fois le canal validé, un canal chiffré de bout en bout inviolable avec Perfect Forward Secrecy est établi.",
      "topics.t_call_e2ee_title": "Comment fonctionnent les appels vocaux et vidéo chiffrés E2EE ?",
      "topics.t_call_e2ee_content": "L'initiation de l'appel utilise une trame de signalisation WebRTC via relais Nostr. Le flux audio/vidéo circule directement en pair à pair (P2P) entre les smartphones avec chiffrement AES-256-GCM. Un code vocal court SAS (Short Authentication String) permet de vérifier verbalement l'absence totale d'interception.",
      "topics.t_call_credit_title": "Les appels nécessitent-ils un forfait téléphonique ou du crédit d'appel ?",
      "topics.t_call_credit_content": "Absolument aucun ! Les appels voix et vidéo d'OrbisNet utilisent exclusivement votre connexion Internet (Wi-Fi ou Données Mobiles 4G/5G). Il n'y a aucun appel téléphonique classique, aucun surcoût et aucun crédit téléphonique requis.",
      "topics.t_call_sas_title": "Qu'est-ce que le code SAS pendant un appel ?",
      "topics.t_call_sas_content": "Le code SAS (Short Authentication String) est calculé à partir des empreintes cryptographiques de la session et s'affiche sur les deux écrans. En lisant ce code à voix haute au début de la conversation, les deux interlocuteurs confirment mathématiquement l'absence totale d'interception (MITM).",
      "topics.t_call_hangup_title": "Comment est garanti le raccrochage et la fin d'appel ?",
      "topics.t_call_hangup_content": "Lors de la fin d'appel, OrbisNet envoie un signal de fermeture immédiat et active un chien de garde (Dead-Peer Watchdog). En cas de perte de signal Internet, la session est coupée automatiquement après 4.5 secondes de silence pour préserver la batterie et la confidentialité.",
      "topics.t4_title": "Comment fonctionne le partage de position GPS ?",
      "topics.t4_content": "La puce satellite GPS de votre smartphone lit vos coordonnées exactes. OrbisNet les chiffre et les transmet en un paquet de données ultra-léger (< 1 Ko) via les relais Nostr, ouvrant instantanément la carte chez votre contact.",
      "topics.t5_title": "Comment fonctionnent le mur social, les stories et les sondages ?",
      "topics.t5_content": "Sur le réseau Nostr, vous pouvez publier des notes publiques (Kind 1), des sondages interactifs et des stories éphémères visibles par l'ensemble du réseau décentralisé. Vous pouvez également basculer en mode 'Entre Amis' pour restreindre la diffusion exclusivement à vos cercles de confiance confirmés via P2P chiffré.",
      "topics.t6_title": "Comment le chiffrement NIP-44 protège-t-il les messages ?",
      "topics.t6_content": "Le standard NIP-44 assure un chiffrement authentifié moderne (ChaCha20-Poly1305) avec masquage de la taille réelle du message (*padding*), empêchant toute écoute réseau d'analyser la longueur ou la nature des échanges.",
      "topics.t7_title": "Quelle est la consommation réelle en données mobiles ?",
      "topics.t7_content": "OrbisNet est conçu pour une frugalité extrême : un message texte ou une réaction consomme moins de 1 Ko de données. Les notes vocales compressées font moins de 15 Ko. Même sur une connexion 4G/5G limitée, l'impact est quasi-nul.",
      "topics.t8_title": "L'application OrbisNet est-elle disponible sur iPhone (Apple iOS) ?",
      "topics.t8_content": "Non, OrbisNet est une exclusivité stricte Android. Les restrictions de l'écosystème fermé d'Apple (sandbox iOS) empêchent les connexions réseau persistantes de fond nécessaires à l'architecture décentralisée. Seul l'écosystème ouvert d'Android permet d'offrir une véritable souveraineté de communication.",
      "topics.t_guard_title": "Comment fonctionne Orbis Guard-LLM contre les arnaques et spams ?",
      "topics.t_guard_content": "Orbis Guard-LLM analyse en temps réel les messages grâce à des embeddings vectoriels locaux. Il détecte les stratagèmes d'urgence, faux liens bancaires et domaines frauduleux. Les messages douteux sont isolés avec alerte visuelle et option de blocage immédiat.",
      "topics.t_reply_title": "Comment Orbis Reply-LLM propose-t-il des réponses intelligentes hors-ligne ?",
      "topics.t_reply_content": "Orbis Reply-LLM analyse le fil de conversation et l'intention du dernier message (salutations, questions, logistique, urgence, remerciements) pour générer 3 pastilles de réponses rapides et polies au-dessus du clavier. L'inférence s'exécute en moins de 10 ms sans aucun appel serveur.",
      "topics.t_ai_learning_title": "Comment les modèles IA s'auto-entraînent-ils sans risque pour ma vie privée ?",
      "topics.t_ai_learning_content": "L'entraînement s'effectue exclusivement sur votre appareil (on-device continual learning). Lorsque vous bloquez un spam ou sélectionnez des suggestions, les poids synaptiques locaux s'adaptent dynamiquement dans le stockage chiffré de l'application. Aucune donnée textuelle, empreinte vectorielle ou statistique n'est envoyée sur un quelconque serveur.",
      "topics.t_edge_title": "À quoi sert la console Edge Vercel (https://orbis-net.vercel.app) ?",
      "topics.t_edge_content": "L'infrastructure Edge hébergée sur Vercel fournit un point de synchronisation souverain et ultra-rapide pour vérifier l'intégrité des mises à jour APK (hash SHA-256), diffuser les annonces critiques ou alertes de sécurité d'urgence, et afficher l'état de santé du réseau avec zéro donnée d'identification personnelle (zéro PII).",
      "topics.t_legal_title": "Politique de Confidentialité & Conditions d'Utilisation",
      "topics.t_legal_content": "OrbisNet est une infrastructure décentralisée souveraine. Aucune donnée personnelle n'est collectée ou transmise à des tiers. Les communications sont chiffrées de bout en bout (AES-256-GCM / BIP-340 / NIP-44). En tant que logiciel fourni « en l'état », chaque utilisateur est légalement et exclusivement responsable de son utilisation et de la conformité avec les lois de sa juridiction.",

      "quota.tag": "SOBRIÉTÉ RÉSEAU",
      "quota.title": "Consommation & Légèreté du Trafic Data",
      "quota.desc": "OrbisNet compresse chaque paquet à l'octet près pour offrir un fonctionnement fluide même sur les réseaux mobiles lents.",
      "quota.th_action": "Action Réalisée",
      "quota.th_cost": "Trafic Data",
      "quota.th_details": "Détails Techniques",
      "quota.row1_title": "Message Texte & DMs Privés",
      "quota.row1_cost": "< 1 Ko",
      "quota.row1_desc": "WebSockets décentralisés Nostr • Chiffrement authentifié NIP-44 • Instantané",
      "quota.row_fallback_title": "Appel Vocal Chiffré E2EE",
      "quota.row_fallback_cost": "~25 Ko / sec",
      "quota.row_fallback_desc": "Flux audio compressé direct P2P • Chiffrement AES-256-GCM • Zéro téléphonie classique",
      "quota.row_call_title": "Appel Vidéo Chiffré E2EE",
      "quota.row_call_cost": "Adaptatif",
      "quota.row_call_desc": "WebRTC P2P direct • Débit optimisé selon la connexion Wi-Fi / 4G / 5G",
      "quota.row2_title": "Partage de Position GPS",
      "quota.row2_cost": "< 1 Ko",
      "quota.row2_desc": "Coordonnées géographiques ultra-compactes ~40 octets via Nostr",
      "quota.row3_title": "Réactions Emoji & Accusés de Réception",
      "quota.row3_cost": "< 1 Ko",
      "quota.row3_desc": "Événement Nostr NIP-25 allégé et instantané",
      "quota.row4_title": "Publication Sociale & Stories 24h",
      "quota.row4_cost": "< 5 Ko",
      "quota.row4_desc": "Diffusion sur relais Nostr décentralisés ou P2P direct entre cercles de confiance",
      "quota.row5_title": "Note Vocale Audio (3 à 5 secondes)",
      "quota.row5_cost": "< 15 Ko",
      "quota.row5_desc": "Flux audio AMR/AAC compressé ZLIB Deflater • Transmission instantanée",
      "quota.row_ai_title": "Bouclier IA Guard-LLM & Suggestions Reply-LLM",
      "quota.row_ai_cost": "0 Ko Data",
      "quota.row_ai_desc": "Inférence neuronale 100% CPU locale • Zéro requête distante • Zéro télémétrie",

      "dl.title": "Téléchargez OrbisNet dès Aujourd'hui",
      "dl.desc": "Reprenez le contrôle de vos communications. Téléchargez l'application souveraine décentralisée sans magasin centralisé.",
      "dl.btn_apk": "Télécharger l'APK Officiel",
      "dl.qr_badge": "Scan Rapide",
      "dl.qr_title": "Installation Directe Smartphone",
      "dl.qr_desc": "Scannez ce QR Code avec l'appareil photo de votre smartphone Android pour lancer le téléchargement immédiat.",

      "legal.modal_title": "Confidentialité & Conditions",
      "legal.badge": "Souverain • Réseau Décentralisé Nostr",
      "legal.tab_privacy": "Confidentialité",
      "legal.tab_terms": "Conditions & Légal",
      "legal.p1_title": "1. Architecture Décentralisée & Zéro Profilage",
      "legal.p1_desc": "OrbisNet ne dispose d'aucun serveur central d'écoute ou de profilage. Les échanges transitent via des relais décentralisés Nostr mondiaux ou directement en pair à pair WebRTC.",
      "legal.p2_title": "2. Cryptographie de Bout en Bout Matérielle",
      "legal.p2_desc": "Toutes les communications privées sont chiffrées avec NIP-44, AES-256-GCM, signatures BIP-340 Schnorr et clés secp256k1 scellées dans l'Android KeyStore TEE matériel.",
      "legal.p3_title": "3. Stockage Exclusivement Local & Souverain",
      "legal.p3_desc": "L'historique de vos discussions, clés secrètes (nsec) et archives est conservé exclusivement sur votre appareil. Vous pouvez exporter une sauvegarde chiffrée (PBKDF2) ou détruire vos données à tout instant.",
      "legal.p4_title": "4. Réseau Nostr & Confidentialité des Données",
      "legal.p4_desc": "Sur Nostr, vos publications publiques sont relayées par les serveurs WebSocket choisis. Les discussions directes privées sont chiffrées de bout en bout et indéchiffrables par les relais.",
      "legal.t1_title": "1. Licence d'Utilisation Personnelle & Fourniture « En l'état »",
      "legal.t1_desc": "OrbisNet est un logiciel souverain distribué « en l'état », sans garantie expresse ou implicite de disponibilité ininterrompue ou d'adéquation à un usage particulier.",
      "legal.t2_title": "2. Responsabilité Légale de l'Utilisateur",
      "legal.t2_desc": "Vous reconnaissez être seul responsable de l'usage fait de l'application, de la garde de vos clés privées (nsec) et du respect des lois en vigueur dans votre pays.",
      "legal.t3_title": "3. Décharge de Responsabilité des Développeurs",
      "legal.t3_desc": "Les créateurs et contributeurs d'OrbisNet déclinent toute responsabilité civile ou pénale quant aux utilisations illicites, frauduleuses ou détournées du protocole.",
      "legal.t4_title": "4. Outils d'Assistance & GPS",
      "legal.t4_desc": "Les fonctionnalités de partage GPS et d'alerte sont des outils de solidarité de pair à pair et ne remplacent en aucun cas les services officiels de secours d'urgence.",
      "legal.btn_close": "Compris & Accepter",
      "legal.open_modal_btn": "Consulter les Conditions & Confidentialité",

      "footer.privacy": "Confidentialité",
      "footer.terms": "Conditions & Légal",
      "footer.tagline": "Réseau social et messagerie décentralisée souveraine Nostr propulsé par ShaDevPro."
    },

    en: {
      "nav.features": "Features",
      "nav.security": "Security & Cryptography",
      "nav.help": "Help Center",
      "nav.quota": "Network Efficiency",
      "nav.download": "Download",
      "nav.get_app": "Get APK",

      "hero.pill": "Decentralized Nostr Protocol • WebSockets & E2EE WebRTC for Android",
      "hero.title_part1": "The Free & Decentralized Internet.",
      "hero.title_part2": "Zero Central Server. Zero Censorship.",
      "hero.subtitle": "The sovereign communication platform powered by the decentralized Nostr network (WebSockets) and end-to-end encrypted WebRTC calls. BIP-340 Schnorr signatures, NIP-44 encryption, and 100% on-device AI.",
      "hero.download_btn": "Download OrbisNet v1.1.0",
      "hero.guide_btn": "Explore Guide & FAQ",
      "hero.telegram_btn": "Telegram Community",
      "dl.btn_telegram": "Telegram Community",
      "dl.btn_github": "Web & Release Repo",
      "dl.checksum_title": "Cryptographic Checksum",
      "dl.copy_btn": "Copy",
      "dl.copied_btn": "✓ Copied!",

      "badges.encryption_title": "BIP-340 & NIP-44",
      "badges.encryption_sub": "Schnorr & E2EE Crypto",
      "badges.offline_title": "Decentralized Relays",
      "badges.offline_sub": "Open WebSockets wss://",
      "badges.hardware_title": "Android KeyStore TEE",
      "badges.hardware_sub": "Hardware-Backed Enclave",

      "mockup.peer_name": "Nostr Peer • npub1...",
      "mockup.status": "● Connected • Nostr Relays (wss://)",
      "mockup.msg1": "⚡ Connected to decentralized wss://relay.damus.io. BIP-340 Schnorr signatures active.",
      "mockup.msg2": "Great! Private exchanges end-to-end encrypted via NIP-44, zero middlemen.",
      "mockup.gps": "🗺️ GPS: 33.5731° N, 7.5898° W",
      "mockup.msg3": "E2EE WebRTC call ready • Guard-LLM AI shield active 🛡️",
      "mockup.placeholder": "Encrypted Nostr message...",

      "features.tag": "SOVEREIGN PILLARS",
      "features.title": "Decentralized Digital Freedom",
      "features.desc": "Everything a modern social network and messenger offers, powered by decentralized relays and mathematical encryption.",
      "features.f1_title": "Decentralized Nostr Messaging",
      "features.f1_text": "Instant messaging via global Nostr relays over WebSockets. End-to-end NIP-04 and NIP-44 encryption guarantees absolute confidentiality of your private messages.",
      "features.f2_title": "Sovereign Identity & BIP-340 Signatures",
      "features.f2_text": "Sovereign cryptographic identity (npub/nsec keypairs). No email or phone number required. Every post and message is signed using Schnorr (secp256k1).",
      "features.f3_title": "Encrypted E2EE Voice & Video Calls",
      "features.f3_text": "Peer-to-peer WebRTC calls encrypted with AES-256-GCM. Verbal SAS verification against wiretapping with zero reliance on traditional telephony.",
      "features.f4_title": "Global Kind 1 Social Feed & 24h Stories",
      "features.f4_text": "Publish open public notes on the global Nostr network or share ephemeral stories and polls exclusively with your trusted circles.",
      "features.f5_title": "NIP-44 Authenticated Encryption",
      "features.f5_text": "Modern end-to-end encryption standard for Nostr: secp256k1 Diffie-Hellman key derivation, ChaCha20-Poly1305 seal, and size-padding defense against traffic analysis.",
      "features.f6_title": "V3 Encrypted Vault & PBKDF2 Backups",
      "features.f6_text": "Full backup derived with PBKDF2 (100,000 iterations), smart storage cleanup, and phone-to-phone migration without centralized cloud storage.",
      "features.f7_title": "Voice Notes & Interactive Waveform",
      "features.f7_text": "Ultra-dense ZLIB compression, responsive touch scrubbing, and smooth playback speed switching (1.0x / 1.5x / 2.0x), dispatched as encrypted data.",
      "features.f8_title": "Sovereign Contacts & Trust Circles",
      "features.f8_text": "Decentralized contact management with mutual QR-code verification, trust circles (Family, Friends, Public), and intelligent local spam filtering.",
      "features.f9_title": "Edge Console & Sovereign Updates",
      "features.f9_text": "Decentralized synchronization with Vercel Edge infrastructure (https://orbis-net.vercel.app) for emergency announcements, SHA-256 integrity verification, and zero-tracking diagnostics.",
      "features.f_guard_title": "Orbis Guard-LLM: Anti-Scam Shield",
      "features.f_guard_text": "Real-time vectorized semantic message analysis. Instant detection of fake banking URLs, phishing, and scams with tri-state classification (Safe, Suspicious, Danger) and 1-tap blocking.",
      "features.f_reply_title": "Orbis Reply-LLM: Smart Contextual Replies",
      "features.f_reply_text": "Instant on-device generation of 3 polite, relevant suggestion chips above the composer in chats across French, English, and Arabic.",
      "features.f_ai_engine_title": "100% Local CPU Vectorized AI Engine",
      "features.f_ai_engine_text": "Proprietary 100% native Kotlin neural models (< 2MB, < 10ms inference). Dynamic on-device continual training from user feedback with zero cloud servers and zero data leaks.",

      "security.tag": "FORTRESS ARCHITECTURE",
      "security.title": "Deep Multi-Layer Defense",
      "security.desc": "Every layer of OrbisNet is engineered to withstand censorship, network wiretaps, and physical forensic extraction.",
      "security.s1_title": "BIP-340 Schnorr Signatures & secp256k1",
      "security.s1_text": "Native Nostr cryptographic authentication: every event and direct message is mathematically signed using your Schnorr private key, ensuring absolute tamper-proof authenticity.",
      "security.s2_title": "NIP-44 Authenticated Encryption & PFS",
      "security.s2_text": "Modern end-to-end direct message encryption with ChaCha20-Poly1305, shared key derivation, and size-padding traffic analysis protection.",
      "security.s3_title": "At-Rest Storage via Android KeyStore TEE",
      "security.s3_text": "Local databases and private keys are sealed by a hardware AES-GCM master key managed in the processor's TEE secure enclave (anti-ADB dump).",
      "security.s4_title": "PBKDF2 100k Iterations Vault Derivation",
      "security.s4_text": "Your `.orbis` vault archives resist brute-force attacks thanks to slow PBKDF2 hashing and a 128-bit random salt.",
      "security.s5_title": "Encrypted Calls with SAS Verbal Code",
      "security.s5_text": "Direct AES-256-GCM audio/video streaming over WebSockets/WebRTC, verbal SAS verification against MITM interception, and guaranteed termination under 4.5s (Dead-Peer Watchdog).",
      "security.s6_title": "Digital Signatures & Anti-Tampering",
      "security.s6_text": "Every Nostr frame and post is cryptographically signed. Package integrity is validated mathematically before execution to block tampering.",
      "security.s7_title": "Volatile RAM Zeroization & Zero-Trace",
      "security.s7_text": "Ephemeral session keys and decrypted buffers are overwritten in RAM immediately after use (`fill(0)`), rendering cold-boot memory dump attacks ineffective.",
      "security.s8_title": "Real-Time Watchdog & Anti-Wiretap",
      "security.s8_text": "Active Dead-Peer Watchdog terminates sessions after 4.5 seconds of network anomaly or silence, preventing hanging connections.",
      "security.s9_title": "100% Offline Vectorized AI Engine",
      "security.s9_text": "Both LLMs (Guard & Reply) run 100% locally on CPU via a native Kotlin matrix engine. Training data, private text, and vector embeddings never leave your device.",
      "security.s10_title": "Hardware Benchmark & Granular Model Control",
      "security.s10_text": "On-device testing suite measuring sub-millisecond CPU latency (< 1ms), throughput, and memory footprint, with granular toggles disabled by default.",
      "security.s11_title": "Duress PIN & Anti-Coercion Decoy Profile",
      "security.s11_text": "Hardware-level panic defense: entering an alternate duress PIN unlocks a harmless decoy profile while silently purging or locking cryptographic session keys under physical coercion.",
      "security.s12_title": "Vercel Edge Telemetry & SHA-256 Verification",
      "security.s12_text": "Sovereign Edge backend (https://orbis-net.vercel.app) for broadcasting emergency announcements, verifying APK signatures via SHA-256, and zero-PII network diagnostics.",

      "help.tag": "OFFICIAL GUIDE & HELP CENTER",
      "help.title": "How Does OrbisNet Work?",
      "help.desc": "Discover the official guide to mastering the decentralized Nostr network, E2EE WebRTC calls, and sovereign security.",
      "help.search_placeholder": "Search topics (e.g. nostr, relays, webrtc, calls, keys, npub, ai)...",
      "help.cat_all": "All",
      "help.cat_start": "🚀 Getting Started",
      "help.cat_nostr": "⚡ Nostr & Relays",
      "help.cat_chat": "💬 Messaging",
      "help.cat_calls": "📞 E2EE Calls",
      "help.cat_ai": "🧠 AI & LLM",
      "help.cat_sec": "🔐 Security",
      "help.cat_sys": "⚙️ System & Network",

      "topics.t_telegram_title": "How to get updates & join the community?",
      "topics.t_telegram_content": "Join our official Telegram community (t.me/orbis_community) or visit the website to download new signed APK releases directly, receive security alerts, and participate in community discussions and feedback.",
      "topics.t1_title": "How does OrbisNet operate without a central server?",
      "topics.t1_content": "OrbisNet relies on the decentralized Nostr protocol. The app connects directly over secure WebSockets (wss://) to a global mesh of independent relays over Wi-Fi or Cellular Mobile Data (4G/5G). No single company or server controls your data, eliminating censorship risks.",
      "topics.t2_title": "What is the Nostr protocol and npub / nsec keys?",
      "topics.t2_content": "Nostr is a decentralized communication protocol with no central server or governing authority. Your account is composed of a cryptographic keypair: your public key (npub) acts as your shareable address, and your private key (nsec) stays on your device to sign your messages (BIP-340 Schnorr). No email address or phone number is required to use Nostr.",
      "topics.t3_title": "How does key pairing and security exchange work?",
      "topics.t3_content": "On first contact, peers exchange public keys via QR Code scan or automated secure exchange over Nostr relays. Once verified, an impenetrable end-to-end encrypted channel with Perfect Forward Secrecy is established.",
      "topics.t_call_e2ee_title": "How do E2EE encrypted voice and video calls work?",
      "topics.t_call_e2ee_content": "Call signaling uses a WebRTC frame over Nostr relays. Audio/video streams directly peer-to-peer (P2P) between devices encrypted in AES-256-GCM. A short SAS (Short Authentication String) code allows verbal verification against wiretapping.",
      "topics.t_call_credit_title": "Do calls require telephone plans or call credit?",
      "topics.t_call_credit_content": "None at all! OrbisNet voice and video calls use only your Internet connection (Wi-Fi or Mobile Data 4G/5G). There are no cellular carrier calls, no extra fees, and no phone credit required.",
      "topics.t_call_sas_title": "What is the SAS security code during a call?",
      "topics.t_call_sas_content": "The Short Authentication String (SAS) code is derived from session crypto fingerprints and shown on both screens. Reading this short code aloud mathematically confirms zero intermediary wiretapping (MITM).",
      "topics.t_call_hangup_title": "How is reliable hangup and call termination guaranteed?",
      "topics.t_call_hangup_content": "Upon ending a call, OrbisNet transmits an immediate teardown signal and activates a Dead-Peer watchdog. In case of network drop, the session terminates automatically after 4.5 seconds of silence to preserve battery and privacy.",
      "topics.t4_title": "How does GPS location sharing work?",
      "topics.t4_content": "Your smartphone satellite GPS chip extracts exact coordinates. OrbisNet encrypts and transmits them in an ultra-compact data packet (< 1 KB) over Nostr relays, opening maps directly on the recipient's device.",
      "topics.t5_title": "How do social feeds, stories, and polls operate?",
      "topics.t5_content": "On Nostr, you can publish public notes (Kind 1), interactive polls, and ephemeral 24h stories visible across the decentralized network. You can also switch to 'Friends-Only' mode to restrict distribution strictly to trusted peer circles via encrypted P2P.",
      "topics.t6_title": "How does NIP-44 encryption protect messages?",
      "topics.t6_content": "The NIP-44 standard provides modern authenticated encryption (ChaCha20-Poly1305) with payload size padding, preventing network eavesdroppers from deducing message type or content length.",
      "topics.t7_title": "What is the actual mobile data consumption?",
      "topics.t7_content": "OrbisNet is engineered for extreme data frugality: a text message or reaction consumes less than 1 KB of data. Compressed voice notes take under 15 KB. Even on slow mobile networks, impact is negligible.",
      "topics.t8_title": "Is OrbisNet available on iPhone (Apple iOS)?",
      "topics.t8_content": "No, OrbisNet is strictly exclusive to Android. Apple's closed iOS sandbox prohibits persistent background socket connections required by decentralized architectures. Only Android's open architecture enables true communication sovereignty.",
      "topics.t_guard_title": "How does Orbis Guard-LLM detect scams and spam?",
      "topics.t_guard_content": "Orbis Guard-LLM analyzes inbound messages using local vector embeddings. It spots urgency pressure tactics, spoofed bank warnings, and phishing domains. Suspicious messages are safely isolated with risk warnings and 1-tap blocking.",
      "topics.t_reply_title": "How does Orbis Reply-LLM generate smart replies offline?",
      "topics.t_reply_content": "Orbis Reply-LLM analyzes conversation context and intent (greetings, questions, confirmations, urgency, gratitude) to generate 3 actionable suggestion chips above the keyboard. Inference executes in under 10ms with zero internet connection or background requests.",
      "topics.t_ai_learning_title": "How do the AI models train without compromising privacy?",
      "topics.t_ai_learning_content": "Model adaptation happens exclusively on your device (on-device continual learning). When you block a spam message or pick reply suggestions, local neural weights adapt dynamically within the app's encrypted storage. No text, vector weights, or statistics are ever transmitted to any remote server.",
      "topics.t_edge_title": "What is the purpose of the Vercel Edge console (https://orbis-net.vercel.app)?",
      "topics.t_edge_content": "The Edge infrastructure hosted on Vercel provides an ultra-fast, sovereign synchronization endpoint to verify APK update integrity (SHA-256 hash), broadcast critical emergency alerts, and monitor network health with zero personally identifiable information (zero PII).",
      "topics.t_legal_title": "Privacy Policy & Terms of Service",
      "topics.t_legal_content": "OrbisNet is a sovereign decentralized infrastructure. No personal data is collected or sent to third-party servers. All communications are end-to-end encrypted (AES-256-GCM / BIP-340 / NIP-44). As software provided 'as is', each user remains solely and legally responsible for their usage and compliance with local laws.",

      "quota.tag": "NETWORK EFFICIENCY",
      "quota.title": "Data Traffic Efficiency & Speed",
      "quota.desc": "OrbisNet optimizes every byte to deliver lightning-fast communication even on slow mobile networks.",
      "quota.th_action": "Action Performed",
      "quota.th_cost": "Data Traffic",
      "quota.th_details": "Technical Details",
      "quota.row1_title": "Text Message & Private DMs",
      "quota.row1_cost": "< 1 KB",
      "quota.row1_desc": "Decentralized Nostr WebSockets • NIP-44 authenticated encryption • Instant",
      "quota.row_fallback_title": "E2EE Encrypted Voice Call",
      "quota.row_fallback_cost": "~25 KB / sec",
      "quota.row_fallback_desc": "Direct P2P compressed audio stream • AES-256-GCM encryption • Zero legacy telephony",
      "quota.row_call_title": "E2EE Encrypted Video Call",
      "quota.row_call_cost": "Adaptive",
      "quota.row_call_desc": "Direct P2P WebRTC • Dynamically optimized for Wi-Fi / 4G / 5G connectivity",
      "quota.row2_title": "GPS Location Sharing",
      "quota.row2_cost": "< 1 KB",
      "quota.row2_desc": "Ultra-compact coordinates ~40 bytes over Nostr",
      "quota.row3_title": "Emoji Reaction & Delivery ACK",
      "quota.row3_cost": "< 1 KB",
      "quota.row3_desc": "Lightweight single-frame Nostr NIP-25 event",
      "quota.row4_title": "Social Post & 24h Stories",
      "quota.row4_cost": "< 5 KB",
      "quota.row4_desc": "Broadcast over Nostr relays or direct encrypted P2P between circles",
      "quota.row5_title": "Voice Audio Note (3 to 5 seconds)",
      "quota.row5_cost": "< 15 KB",
      "quota.row5_desc": "AMR/AAC audio compressed with Deflater ZLIB • Instant dispatch",
      "quota.row_ai_title": "AI Guard-LLM & Reply-LLM Analysis",
      "quota.row_ai_cost": "0 KB Data",
      "quota.row_ai_desc": "100% On-Device CPU inference • 0 bytes sent or received • Zero telemetry",

      "dl.title": "Download OrbisNet Today",
      "dl.desc": "Reclaim control of your communications. Download the sovereign decentralized app directly without centralized stores.",
      "dl.btn_apk": "Download Official APK",
      "dl.qr_badge": "Quick Scan",
      "dl.qr_title": "Direct Smartphone Install",
      "dl.qr_desc": "Scan this QR Code with your Android phone camera to start instant download.",

      "legal.modal_title": "Privacy & Terms of Service",
      "legal.badge": "Sovereign • Decentralized Nostr Network",
      "legal.tab_privacy": "Privacy Policy",
      "legal.tab_terms": "Terms of Use",
      "legal.p1_title": "1. Decentralized Architecture & Zero Profiling",
      "legal.p1_desc": "OrbisNet has no central servers for tracking or profiling. Communications flow over decentralized global Nostr relays or directly peer-to-peer over WebRTC.",
      "legal.p2_title": "2. Hardware-Backed End-to-End Encryption",
      "legal.p2_desc": "All private communications are encrypted with NIP-44, AES-256-GCM, BIP-340 Schnorr signatures, and secp256k1 keys sealed in Android KeyStore TEE hardware.",
      "legal.p3_title": "3. Exclusively Local & Sovereign Storage",
      "legal.p3_desc": "Your chat history, private keys (nsec), and archives are kept strictly on your device. You can export a PBKDF2-encrypted backup or wipe everything at any time.",
      "legal.p4_title": "4. Nostr Network & Data Privacy",
      "legal.p4_desc": "On Nostr, public posts are broadcast to chosen WebSocket relays. Private direct discussions are end-to-end encrypted and unreadable by relay nodes.",
      "legal.t1_title": "1. Personal Use License & 'As-Is' Provision",
      "legal.t1_desc": "OrbisNet is sovereign software distributed 'as-is', without warranty of any kind, express or implied, including uninterrupted availability or fitness for a particular purpose.",
      "legal.t2_title": "2. User Legal Responsibility & Compliance",
      "legal.t2_desc": "You acknowledge that you are solely responsible for your use of the application, keeping your private keys safe, and complying with all applicable laws in your jurisdiction.",
      "legal.t3_title": "3. Developer Limitation of Liability",
      "legal.t3_desc": "The creators, developers, and contributors of OrbisNet expressly disclaim any liability for any damages or unlawful activities resulting from the misuse of this software.",
      "legal.t4_title": "4. Emergency Features & Location",
      "legal.t4_desc": "GPS location sharing features are peer-to-peer assistance tools and do not replace official public emergency services.",
      "legal.btn_close": "Understood & Accept",
      "legal.open_modal_btn": "View Terms & Privacy Policy",

      "footer.privacy": "Privacy Policy",
      "footer.terms": "Terms of Use",
      "footer.tagline": "Sovereign decentralized Nostr messenger and social network powered by ShaDevPro."
    },

    ar: {
      "nav.features": "المميزات",
      "nav.security": "الأمان والتشفير",
      "nav.help": "مركز المساعدة",
      "nav.quota": "استهلاك الشبكة",
      "nav.download": "تحميل التطبيق",
      "nav.get_app": "تحميل APK",

      "hero.pill": "بروتوكول Nostr اللامركزي • WebSockets ومكالمات WebRTC مشفرة لأندرويد",
      "hero.title_part1": "الإنترنت الحر واللامركزي.",
      "hero.title_part2": "بلا خادم مركزي. بلا رقابة.",
      "hero.subtitle": "المنصة السيادية للتواصل المدعومة بشبكة Nostr اللامركزية (WebSockets) ومكالمات WebRTC المشفرة طرفاً لطرف. تواقيع Schnorr BIP-340 وتشفير NIP-44 وذكاء اصطناعي محلي 100%.",
      "hero.download_btn": "تحميل OrbisNet v1.1.0",
      "hero.guide_btn": "دليل الاستخدام والأسئلة الشائعة",
      "hero.telegram_btn": "مجتمع تيليجرام الرسمي",
      "dl.btn_telegram": "مجتمع تيليجرام",
      "dl.btn_github": "مستودع الموقع والإصدارات",
      "dl.checksum_title": "البصمة الرقمية للتحقق",
      "dl.copy_btn": "نسخ",
      "dl.copied_btn": "✓ تم النسخ!",

      "badges.encryption_title": "BIP-340 و NIP-44",
      "badges.encryption_sub": "توقيع Schnorr وتشفير E2EE",
      "badges.offline_title": "ترحيل لامركزي",
      "badges.offline_sub": "خوادم ترحيل مفتوحة wss://",
      "badges.hardware_title": "حماية KeyStore TEE",
      "badges.hardware_sub": "حماية عتادية بالمعالج",

      "mockup.peer_name": "نظير Nostr • npub1...",
      "mockup.status": "● متصل • ترحيل Nostr (wss://)",
      "mockup.msg1": "⚡ متصل بترحيل لامركزي wss://relay.damus.io. تواقيع Schnorr BIP-340 نشطة.",
      "mockup.msg2": "ممتاز! محادثات خاصة مشفرة طرفاً لطرف عبر NIP-44 بدون أي وسيط.",
      "mockup.gps": "🗺️ GPS: 33.5731° N, 7.5898° W",
      "mockup.msg3": "مكالمات WebRTC E2EE جاهزة • درع Guard-LLM نشط 🛡️",
      "mockup.placeholder": "رسالة مشفرة Nostr...",

      "features.tag": "الأركان السيادية",
      "features.title": "الحرية الرقمية اللامركزية",
      "features.desc": "كل ما تقدمه شبكات التواصل والمراسلة الحديثة، عبر خوادم ترحيل لامركزية وتشفير حسابي مطلق.",
      "features.f1_title": "مراسلة Nostr اللامركزية",
      "features.f1_text": "مراسلة فورية عبر خوادم ترحيل Nostr العالمية بواسطة WebSockets. تشفير شامل NIP-04 و NIP-44 يضمن السرية التامة للمحادثات الخاصة.",
      "features.f2_title": "هوية سيادية وتواقيع BIP-340",
      "features.f2_text": "هوية تشفيرية سيادية (مفاتيح npub/nsec). لا حاجة لبريد إلكتروني أو رقم هاتف. يتم توقيع كل منشور ورسالة عبر Schnorr (secp256k1).",
      "features.f3_title": "مكالمات صوت وفيديو مشفرة E2EE",
      "features.f3_text": "مكالمات WebRTC بنظام الند للند مشفرة بـ AES-256-GCM. توثيق برمز SAS الشفهي لمكافحة التنصت دون أي اعتماد على شبكات الهاتف التقليدية.",
      "features.f4_title": "موجز اجتماعي عالمي وقصص 24 ساعة",
      "features.f4_text": "انشر ملاحظات عامة على شبكة Nostr العالمية أو شارك قصصاً مؤقتة واستطلاعات رأي حصرياً مع دوائر أصدقائك الموثوقين.",
      "features.f5_title": "تشفير NIP-44 الموثق",
      "features.f5_text": "معيار التشفير الحديث لـ Nostr: اشتقاق المفاتيح عبر Diffie-Hellman، حماية ChaCha20-Poly1305 وتمويه حجم البيانات لمقاومة تحليل الحركة.",
      "features.f6_title": "خزنة مشفرة V3 ونسخ احتياطي PBKDF2",
      "features.f6_text": "نسخ احتياطي فائق الأمان عبر PBKDF2 (100 ألف دورة)، تنظيف تلقائي للمساحة وترحيل البيانات بين الهواتف دون أي سحابة مركزية.",
      "features.f7_title": "رسائل صوتية ومخطط تفاعلي",
      "features.f7_text": "ضغط فائق ZLIB، تصفح دقيق باللمس مع خيارات سرعة تشغيل ديناميكية (1.0x / 1.5x / 2.0x)، تُرسل كحزم بيانات مشفرة.",
      "features.f8_title": "دليل جهات اتصال سيادي ودوائر ثقة",
      "features.f8_text": "إدارة جهات اتصال لامركزية مع توثيق متبادل عبر رموز QR وتنظيم في دوائر موثوقة (عائلة، أصدقاء، عام) مع تصفية ذكية محلية للرسائل المزعجة.",
      "features.f9_title": "لوحة تحكم Edge وتحديثات سيادية",
      "features.f9_text": "مزامنة آمنة مع بنية Vercel Edge التحتية (https://orbis-net.vercel.app) لتلقي تنبيهات الطوارئ والتحقق من بصمة SHA-256 والتشخيصات المجهولة.",
      "features.f_guard_title": "Orbis Guard-LLM : درع مكافحة الاحتيال",
      "features.f_guard_text": "تحليل دلالي متجهي فوري للرسائل. اكتشاف الروابط البنكية المزيفة والاحتيال وتصنيف ثلاثي (آمن، مشبوه، خطير) مع حظر فوري بنقرة واحدة.",
      "features.f_reply_title": "Orbis Reply-LLM : اقتراحات الرد الذكية",
      "features.f_reply_text": "توليد فوري لـ 3 ردود سريعة ومهذبة ومطابقة للسياق أعلى لوحة الكتابة في المحادثات باللغات الفرنسية والإنجليزية والعربية.",
      "features.f_ai_engine_title": "محرك ذكاء اصطناعي متجهي محلي 100%",
      "features.f_ai_engine_text": "نماذج عصبية سيادية بلغة كوتلن الأصلية 100% (أقل من 2 ميغابايت وسرعة استنتاج أقل من 10 مللي ثانية). تعلّم مستمر محلياً من تفاعلات المستخدم بدون أي خوادم خارجية أو تسريب بيانات.",

      "security.tag": "بنية الحصن الرقمي",
      "security.title": "أمان متعدد الطبقات في العمق",
      "security.desc": "تم تصميم كل طبقة في OrbisNet لمقاومة الرقابة، التنصت على الشبكات، واستخراج البيانات المادي.",
      "security.s1_title": "تواقيع Schnorr BIP-340 ومنحنى secp256k1",
      "security.s1_text": "توثيق تشفيري أصلي في Nostr: يتم توقيع كل حدث ورسالة خاصة حسابياً بمفتاح Schnorr الخاص بك، مما يضمن سلامة مطلقة للبيانات.",
      "security.s2_title": "تشفير NIP-44 الموثق وسرية تامة",
      "security.s2_text": "تشفير حديث طرفاً لطرف للمحادثات الخاصة مع ChaCha20-Poly1305، اشتقاق المفتاح المشترك، وتمويه طول الرسائل لمنع تحليل حركة البيانات.",
      "security.s3_title": "تخزين محمي عبر Android KeyStore TEE",
      "security.s3_text": "قواعد البيانات المحلية والمفاتيح الخاصة مشفرة بمفتاح عتادي AES-GCM داخل بيئة المعالج الآمنة TEE لمقاومة أي سحب عبر ADB.",
      "security.s4_title": "اشتقاق الخزنة عبر PBKDF2 بـ 100 ألف دورة",
      "security.s4_text": "حماية النسخ الاحتياطية ضد هجمات التخمين بفضل 100 ألف تكرار هاش PBKDF2 وملح عشوائي 128 بت.",
      "security.s5_title": "مكالمات مشفرة مع رمز التحقق الشفهي SAS",
      "security.s5_text": "بث صوت وفيديو مشفر بـ AES-256-GCM عبر WebRTC، مطابقة شفهية لرمز SAS لمنع التنصت وضمان إنهاء المكالمة خلال 4.5 ثوانٍ.",
      "security.s6_title": "التوقيع الرقمي ومكافحة التلاعب",
      "security.s6_text": "يتم توقيع كل حزمة ورسالة Nostr رقمياً، مع التحقق الحسابي من سلامة البيانات قبل التنفيذ لمنع أي تزوير.",
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
      "help.desc": "اكتشف الدليل الرسمي لإتقان شبكة Nostr اللامركزية ومكالمات WebRTC E2EE والأمان السيادي.",
      "help.search_placeholder": "ابحث في المواضيع (مثال: nostr، ترحيل، webrtc، مكالمات، مفاتيح، npub، ذكاء اصطناعي)...",
      "help.cat_all": "الكل",
      "help.cat_start": "🚀 البداية",
      "help.cat_nostr": "⚡ شبكة Nostr",
      "help.cat_chat": "💬 المراسلة",
      "help.cat_calls": "📞 المكالمات",
      "help.cat_ai": "🧠 الذكاء الاصطناعي",
      "help.cat_sec": "🔐 الأمان",
      "help.cat_sys": "⚙️ النظام والشبكة",

      "topics.t_telegram_title": "كيفية الحصول على التحديثات والانضمام إلى المجتمع؟",
      "topics.t_telegram_content": "انضم إلى مجتمعنا الرسمي على تيليجرام (t.me/orbis_community) أو تصفح الموقع الرسمي لتحميل إصدارات APK الموقعة مباشرة، وتلقي تنبيهات الأمان والمشاركة في النقاشات.",
      "topics.t1_title": "كيف يعمل OrbisNet بدون خادم مركزي؟",
      "topics.t1_content": "يعتمد OrbisNet على بروتوكول Nostr اللامركزي. يتصل التطبيق مباشرة عبر WebSockets آمنة (wss://) بشبكة عالمية من خوادم الترحيل المستقلة عبر Wi-Fi أو بيانات الهاتف (4G/5G). لا توجد شركة أو خادم يتحكم ببياناتك، مما يقضي على الرقابة تماماً.",
      "topics.t2_title": "ما هو بروتوكول Nostr وما هي مفاتيح npub و nsec؟",
      "topics.t2_content": "Nostr هو بروتوكول تواصل لامركزي لا يخضع لأي سلطة مركزية. يتكون حسابك من زوج مفاتيح تشفير: المفتاح العام (npub) كعنوان للمشاركة، والمفتاح الخاص (nsec) يبقى على هاتفك لتوقيع رسائلك عبر تواقيع Schnorr BIP-340. لا تحتاج لأي بريد إلكتروني أو رقم هاتف لإنشاء حساب Nostr.",
      "topics.t3_title": "كيف يتم تبادل مفاتيح الأمان والاقتران؟",
      "topics.t3_content": "عند أول تواصل، يتبادل المستخدمون المفاتيح العامة إما بمسح رمز QR المباشر أو تلقائياً عبر ترحيل Nostr. بمجرد التوثيق، يتم فتح قناة مشفرة ومحمية بسريّة تامة.",
      "topics.t_call_e2ee_title": "كيف تعمل مكالمات الصوت والفيديو المشفرة (E2EE)؟",
      "topics.t_call_e2ee_content": "تستخدم المكالمات إشارات WebRTC عبر ترحيل Nostr. يتم بث الصوت والفيديو بنظام الند للند (P2P) مشفراً بـ AES-256-GCM. ويتيح رمز SAS القصير التحقق الشفهي لمنع أي تنصت.",
      "topics.t_call_credit_title": "هل تتطلب المكالمات باقة هاتفية أو رصيد مكالمات؟",
      "topics.t_call_credit_content": "كلا على الإطلاق! تستخدم مكالمات OrbisNet اتصالك بالإنترنت فقط (Wi-Fi أو بيانات الهاتف 4G/5G). لا توجد أي مكالمات خلوية عادية ولا أي رسوم أو رصيد مطلوب.",
      "topics.t_call_sas_title": "ما هو رمز الأمان SAS أثناء المكالمة؟",
      "topics.t_call_sas_content": "رمز SAS هو كود مشتق يظهر على كلا الشاشتين. بمجرد قراءته صوتياً مع الطرف الآخر، يتأكد الطرفان رياضياً من استحالة وجود أي وسيط متنصت (MITM).",
      "topics.t_call_hangup_title": "كيف يعمل إنهاء المكالمة وضمان إغلاق الخط؟",
      "topics.t_call_hangup_content": "عند إنهاء المكالمة، يرسل التطبيق إشارة إغلاق فورية ويفعل مراقب الاتصال. في حال انقطاع النت، يتم إنهاء الجلسة تلقائياً خلال 4.5 ثوانٍ لحماية البطارية والخصوصية.",
      "topics.t4_title": "كيف تعمل مشاركة الموقع GPS؟",
      "topics.t4_content": "تقرأ شريحة الأقمار الصناعية GPS إحداثياتك بدقة، ويقوم OrbisNet بتشفيرها ونقلها في حزمة بيانات صغيرة جداً (< 1 ك.ب) عبر Nostr لتفتح الخريطة مباشرة عند الطرف الآخر.",
      "topics.t5_title": "كيف تعمل الشبكة الاجتماعية والقصص واستطلاعات الرأي؟",
      "topics.t5_content": "على شبكة Nostr، يمكنك نشر ملاحظات عامة (Kind 1) واستطلاعات رأي تفاعلية وقصصاً مؤقتة لجميع النظراء، أو التبديل لوضع 'بين الأصدقاء' لحصر النشر في دوائر الثقة المقربة بتشفير P2P.",
      "topics.t6_title": "كيف يحمي تشفير NIP-44 الرسائل الخاصة؟",
      "topics.t6_content": "يوفر معيار NIP-44 تشفيراً موثقاً حديثاً مع تمويه طول الرسائل، مما يمنع أي طرف على الشبكة من استنتاج حجم أو طبيعة المحادثة.",
      "topics.t7_title": "كم يستهلك التطبيق من بيانات الهاتف؟",
      "topics.t7_content": "تم تصميم OrbisNet ليكون فائق التوفير: تستهلك الرسالة النصية أو التفاعل أقل من 1 كيلوبايت. والرسائل الصوتية المضغوطة أقل من 15 كيلوبايت، مما يجعله خفيفاً للغاية حتى على أبطأ شبكات الهاتف.",
      "topics.t8_title": "هل يتوفر تطبيق OrbisNet على هواتف آيفون (Apple iOS)؟",
      "topics.t8_content": "كلا، تطبيق OrbisNet حصري تماماً لنظام أندرويد. تمنع قيود نظام آبل المغلق (iOS Sandbox) الاتصالات الشبكية الثابتة في الخلفية المطلوبة للبنية اللامركزية. نظام أندرويد المفتوح هو القادر على دعم السيادة الرقمية التامة.",
      "topics.t_guard_title": "كيف يحمي Orbis Guard-LLM من رسائل الاحتيال والسبام؟",
      "topics.t_guard_content": "يفحص Orbis Guard-LLM كل رسالة واردة محلياً عبر متجهات سياقية. يكتشف أساليب الضغط والاستعجال والتنبيهات البنكية المزيفة ويعزل الروابط الخبيثة مع تصنيف ثلاثي (آمن، مشبوه، خطير) وخيار حظر فوري.",
      "topics.t_reply_title": "كيف يولد Orbis Reply-LLM ردوداً ذكية بدون إنترنت؟",
      "topics.t_reply_content": "يحلل Orbis Reply-LLM سياق المحادثة ومقصد الرسالة الأخيرة ليقدم 3 اقتراحات ردود سريعة ومهذبة أعلى لوحة المفاتيح. تتم المعالجة خلال أقل من 10 مللي ثانية دون أي اتصال بالإنترنت.",
      "topics.t_ai_learning_title": "كيف تتعلم النماذج ذاتياً دون انتهاك الخصوصية؟",
      "topics.t_ai_learning_content": "يتم التدريب والتحديث حصرياً على جهازك (on-device continual learning). عند حظر رسالة مزعجة أو اختيار ردود مقترحة، تتكيف الأوزان العصبية محلياً داخل وحدة التخزين المشفرة للتطبيق دون إرسال أي بيانات خارج هاتفك.",
      "topics.t_edge_title": "ما هي وظيفة لوحة تحكم Edge Vercel (https://orbis-net.vercel.app)؟",
      "topics.t_edge_content": "توفر بنية Edge المستضافة على Vercel نقطة مزامنة سيادية فائقة السرعة للتحقق من سلامة تحديثات APK (بصمة SHA-256) وبث إعلانات الطوارئ ومراقبة صحة الشبكة دون أي جمع لبيانات الهوية الشخصية.",
      "topics.t_legal_title": "سياسة الخصوصية والشروط وإخلاء المسؤولية",
      "topics.t_legal_content": "OrbisNet بنية تحتية لامركزية سيادية. لا يتم جمع أو إرسال أي بيانات إلى أطراف ثالثة. يتم تشفير الاتصالات طرفاً لطرف (AES-256-GCM / BIP-340 / NIP-44). وبما أنه برنامج يُقدَّم \"كما هو\"، يتحمل كل مستخدم وحده المسؤولية القانونية الكاملة عن استخداماته.",

      "quota.tag": "استهلاك الشبكة",
      "quota.title": "كفاءة استهلاك البيانات وسرعة الأداء",
      "quota.desc": "يحسن OrbisNet كل بايت لتقديم أداء فائق السرعة حتى على أبطأ شبكات الهاتف المحمول.",
      "quota.th_action": "العملية",
      "quota.th_cost": "حجم البيانات",
      "quota.th_details": "التفاصيل التقنية",
      "quota.row1_title": "رسالة نصية ورسائل خاصة",
      "quota.row1_cost": "< 1 ك.ب",
      "quota.row1_desc": "ترحيل WebSockets لامركزي عبر Nostr • تشفير NIP-44 موثق • فوري",
      "quota.row_fallback_title": "مكالمة صوتية مشفرة E2EE",
      "quota.row_fallback_cost": "~25 ك.ب / ثانية",
      "quota.row_fallback_desc": "بث صوتي مضغوط ومباشر P2P • تشفير AES-256-GCM • بدون مكالمات هاتفية عادية",
      "quota.row_call_title": "مكالمة فيديو مشفرة E2EE",
      "quota.row_call_cost": "تكيفي",
      "quota.row_call_desc": "WebRTC P2P مباشر • معدل تدفق متكيف مع جودة اتصال Wi-Fi أو 4G / 5G",
      "quota.row2_title": "مشاركة موقع GPS",
      "quota.row2_cost": "< 1 ك.ب",
      "quota.row2_desc": "إحداثيات جغرافية فائقة الصغر ~40 بايت عبر Nostr",
      "quota.row3_title": "تفاعلات الإيموجي وإشعارات الاستلام",
      "quota.row3_cost": "< 1 ك.ب",
      "quota.row3_desc": "حدث Nostr NIP-25 خفيف وفوري",
      "quota.row4_title": "المنشورات الاجتماعية وقصص 24 ساعة",
      "quota.row4_cost": "< 5 ك.ب",
      "quota.row4_desc": "نشر عبر خوادم ترحيل Nostr أو اتصال P2P مباشر بين الدوائر الموثوقة",
      "quota.row5_title": "رسالة صوتية (3 إلى 5 ثوانٍ)",
      "quota.row5_cost": "< 15 ك.ب",
      "quota.row5_desc": "ملف صوتي AMR/AAC مضغوط ZLIB Deflater • إرسال فوري",
      "quota.row_ai_title": "درع Guard-LLM واقتراحات Reply-LLM",
      "quota.row_ai_cost": "0 ك.ب بيانات",
      "quota.row_ai_desc": "معالجة محلية 100% على معالج الهاتف • صفر بايت مرسل أو مستقبل • انعدام تام لأي تتبع",

      "dl.title": "حمّل تطبيق OrbisNet الآن",
      "dl.desc": "استعد السيطرة الكاملة على محادثاتك. حمّل التطبيق السيادي اللامركزي مباشرة بدون متاجر مركزية.",
      "dl.btn_apk": "تحميل ملف APK الرسمي",
      "dl.qr_badge": "مسح سريع",
      "dl.qr_title": "تثبيت مباشر على الهاتف",
      "dl.qr_desc": "امسح رمز الاستجابة السريعة بكاميرا هاتفك لبدء التحميل الفوري.",

      "legal.modal_title": "الخصوصية والشروط القانونية",
      "legal.badge": "سيادي • شبكة Nostr اللامركزية",
      "legal.tab_privacy": "الخصوصية",
      "legal.tab_terms": "الشروط والأحكام",
      "legal.p1_title": "1. بنية لامركزية وانعدام المراقبة",
      "legal.p1_desc": "لا يملك OrbisNet أي خوادم مركزية للمراقبة أو التتبع. تنتقل البيانات عبر خوادم ترحيل Nostr اللامركزية أو مباشرة عبر WebRTC.",
      "legal.p2_title": "2. تشفير شامل طرفاً لطرف معتمد عتادياً",
      "legal.p2_desc": "جميع المراسلات الخاصة مشفرة بتقنيات NIP-44 و AES-256-GCM وتواقيع Schnorr BIP-340 المحمية عتادياً في KeyStore TEE.",
      "legal.p3_title": "3. تخزين محلي حصري وسيادي على الجهاز",
      "legal.p3_desc": "يتم تخزين سجل المحادثات والمفاتيح السرية (nsec) محلياً في هاتفك فقط. يمكنك تصدير نسخة احتياطية مشفرة بـ PBKDF2 أو مسح بياناتك في أي وقت.",
      "legal.p4_title": "4. شبكة Nostr وخصوصية البيانات",
      "legal.p4_desc": "في Nostr، تُبث المنشورات العامة لخوادم الترحيل المختارة. والمحادثات الخاصة مشفرة طرفاً لطرف ولا يمكن لخوادم الترحيل قراءتها.",
      "legal.t1_title": "1. رخصة الاستخدام وتوفير البرنامج 'كما هو'",
      "legal.t1_desc": "OrbisNet برنامج سيادي يُقدم 'كما هو' دون أي ضمانات صريحة أو ضمنية بشأن استمرارية الخدمة أو الملاءمة لغرض معين.",
      "legal.t2_title": "2. المسؤولية القانونية الحصرية للمستخدم",
      "legal.t2_desc": "تقر بأنك المسؤول الوحيد عن استخدامك للتطبيق والحفاظ على أمان مفاتيحك الخاصة (nsec) والالتزام بالقوانين المعمول بها في منطقتك.",
      "legal.t3_title": "3. إخلاء مسؤولية المطورين والمساهمين",
      "legal.t3_desc": "يخلي مطورو ومساهمو OrbisNet مسؤوليتهم المدنية والجنائية تماماً عن أي أضرار أو أنشطة غير قانونية ناتجة عن إساءة استخدام التطبيق.",
      "legal.t4_title": "4. ميزات المساعدة والموقع GPS",
      "legal.t4_desc": "ميزات مشاركة الموقع وتنبيه SOS هي أدوات مساعدة بين النظراء ولا تحل محل خدمات الطوارئ الرسمية في دولتك.",
      "legal.btn_close": "فهمت وأوافق",
      "legal.open_modal_btn": "عرض سياسة الخصوصية والشروط",

      "footer.privacy": "الخصوصية",
      "footer.terms": "الشروط والأحكام",
      "footer.tagline": "شبكة تواصل اجتماعي ومراسلة لامركزية سيادية Nostr مطورة بواسطة ShaDevPro."
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
    apkDownloadUrl: "https://github.com/ShaDevPro/O-R-B-I-S-net/releases/download/OrbisNet-v1.1.0/O.R.B.I.S.apk",
    githubRepoUrl: "https://github.com/ShaDevPro/O-R-B-I-S-net",
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
    document.querySelectorAll('a[download]').forEach(a => {
      a.href = url;
    });
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
