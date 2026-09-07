/**
 * O R B I S Sovereign P2P — Master Application Script
 * Propulsé par ShaDevPro
 * Includes: i18n Dictionary (FR/EN/AR), Search Filter, Accordions, Theme Toggle, RTL Handling.
 */

document.addEventListener('DOMContentLoaded', () => {

  // =========================================================================
  // 1. i18n Localization Dictionary
  // =========================================================================
  const translations = {
    fr: {
      "nav.features": "Fonctionnalités",
      "nav.security": "Sécurité & PFS",
      "nav.help": "Centre d'aide",
      "nav.quota": "Quota GSM",
      "nav.download": "Télécharger",
      "nav.get_app": "Obtenir l'APK",

      "hero.pill": "Protocole Décentralisé GSM • 100% Hors-Ligne pour Android",
      "hero.title_part1": "Communiquez sans Internet.",
      "hero.title_part2": "Sans Serveur. Sans Traces.",
      "hero.subtitle": "La première plateforme sociale et de messagerie souveraine au monde propulsée par le réseau SMS GSM avec chiffrement de niveau militaire AES-256-GCM, Double Ratchet (PFS) et stéganographie invisible.",
      "hero.download_btn": "Télécharger O R B I S v1.1.0",
      "hero.guide_btn": "Explorer le Guide & FAQ",
      "hero.telegram_btn": "Communauté Telegram",
      "dl.btn_telegram": "Communauté Telegram",
      "dl.btn_github": "Dépôt Web & Releases",
      "dl.checksum_title": "Empreinte Cryptographique",
      "dl.copy_btn": "Copier",
      "dl.copied_btn": "✓ Copié !",
      "topics.t_telegram_title": "Comment recevoir les mises à jour et rejoindre la communauté ?",
      "topics.t_telegram_content": "Rejoignez notre communauté Telegram officielle (t.me/orbis_community) pour télécharger les nouvelles versions APK signées en direct, recevoir les alertes de sécurité, échanger et participer aux discussions et retours d'expérience.",


      "badges.encryption_title": "AES-256-GCM & PFS",
      "badges.encryption_sub": "Double Ratchet Militaire",
      "badges.offline_title": "Zéro Dépendance Cloud",
      "badges.offline_sub": "GSM SMS P2P Direct",
      "badges.hardware_title": "Android KeyStore",
      "badges.hardware_sub": "Protection Matérielle",

      "mockup.peer_name": "Sovereign Mesh Peer",
      "mockup.status": "● Chiffré E2EE • GSM",
      "mockup.msg1": "🔐 Canaux Double Ratchet initialisés avec succès. Clé éphémère KDF synchronisée.",
      "mockup.msg2": "Parfait ! Partage de position d'urgence chiffrée envoyé en 1 seul SMS.",
      "mockup.gps": "🗺️ GPS: 33.5731° N, 7.5898° W",
      "mockup.msg3": "Stéganographie active : charge utile dissimulée sous texte porteur 🛡️",
      "mockup.placeholder": "Message chiffré SMS...",

      "features.tag": "PILIERS SOUVERAINS",
      "features.title": "L'Autonomie Numérique Réinventée",
      "features.desc": "Tout ce qu'un réseau social et une messagerie moderne offrent, sans aucun serveur tiers ni connexion Internet requise.",
      "features.f1_title": "Messagerie P2P & Double Ratchet",
      "features.f1_text": "Chaque message génère une nouvelle clé de dérivation via HMAC-SHA256 (PFS). Dès lecture, la clé est écrasée en mémoire vive (`fill(0)`).",
      "features.f2_title": "Notes Vocales & Waveform Interactive",
      "features.f2_text": "Compression ZLIB/Deflater ultra-dense, scrubbing dynamique au toucher et sélecteur de vitesse de lecture fluide (1.0x / 1.5x / 2.0x).",
      "features.f3_title": "Géolocalisation & Widget SOS",
      "features.f3_text": "Émission d'alerte de détresse avec coordonnées GPS vers vos cercles de confiance en 1 seul clic depuis l'écran d'accueil Android.",
      "features.f4_title": "Mur Social & Stories 24h Entre Amis",
      "features.f4_text": "Partagez vos pensées, sondages décentralisés et stories éphémères exclusivement avec vos amis confirmés et cercles de confiance, sans fuite vers vos contacts ordinaires.",
      "features.f7_title": "Appels Vocaux Chiffrés E2EE",
      "features.f7_text": "Signalisation GSM SMS hors-ligne, streaming audio direct AES-256-GCM, validation du code de sécurité SAS et bascule automatique vers appel GSM ordinaire en cas de solde SMS épuisé.",
      "features.f5_title": "Stéganographie SMS Invisible",
      "features.f5_text": "Dissimule les données chiffrées sous forme de caractères Unicode invisibles de largeur nulle au milieu de textes anodins (anti-censure).",
      "features.f6_title": "Coffre Chiffré V3 & Migration P2P",
      "features.f6_text": "Sauvegarde intégrale dérivée par PBKDF2 (100 000 itérations), nettoyage intelligent du stockage et migration entre smartphones sans le cloud.",
      "features.f8_title": "Extraction OTP & Filtre Intelligent",
      "features.f8_text": "Détection instantanée et copie 1-clic des codes 2FA/bancaires (Google, WhatsApp, banques) avec priorisation en haut de liste et isolation des spams sans aucun accès internet.",
      "features.f9_title": "Multi-SIM & Routage GSM Natif",
      "features.f9_text": "Prise en charge native Double SIM Android avec basculement automatique selon la couverture réseau et suivi rigoureux des quotas SMS par opérateur.",
      "features.f_guard_title": "ORBIS Guard-LLM : Bouclier Anti-Arnaque",
      "features.f_guard_text": "Analyse sémantique vectorisée en temps réel de vos SMS. Détection des faux liens bancaires, smishing et arnaques avec classification tri-état (Sûr, Suspect, Danger) et blocage 1-clic.",
      "features.f_reply_title": "ORBIS Reply-LLM : Suggestions Intelligentes",
      "features.f_reply_text": "Génération instantanée de 3 réponses rapides, polies et contextuelles au-dessus de votre saisie dans le chat chiffré et les SMS classiques, en français, anglais et arabe.",
      "features.f_ai_engine_title": "Moteur IA Vectorisé & Auto-Entraînement",
      "features.f_ai_engine_text": "Modèles neuronaux propriétaires 100% Kotlin natif (< 2 Mo, inférence < 10 ms). Auto-apprentissage continu sur l'appareil à partir de vos retours, sans aucun serveur ni télémétrie.",

      "security.tag": "ARCHITECTURE FORTERESSE",
      "security.title": "Sécurité Multi-Couches en Profondeur",
      "security.desc": "Chaque couche d'O R B I S a été auditée et durcie pour résister à la rétro-ingénierie, aux écoutes réseau et aux extractions physiques.",
      "security.s1_title": "Chiffrement Asymétrique RSA-2048",
      "security.s1_text": "Paires de clés privées/publiques générées localement sur l'appareil. Signature cryptographique de chaque paquet pour garantir l'authenticité de l'expéditeur.",
      "security.s2_title": "Double Ratchet & Perfect Forward Secrecy",
      "security.s2_text": "Renouvellement automatique et irréversible des clés de session après chaque message. Même en cas de compromission physique, l'historique reste indéchiffrable.",
      "security.s3_title": "Stockage au Repos via Android KeyStore",
      "security.s3_text": "Les bases de données locales et fichiers sont scellés par une clé matérielle AES-GCM gérée dans la zone sécurisée TEE de votre processeur (anti-extraction ADB).",
      "security.s4_title": "Dérivation de Coffre PBKDF2 100k Itérations",
      "security.s4_text": "Vos archives `.orbis` résistent aux attaques par force brute grâce au hachage ralenti PBKDF2 combiné à un sel aléatoire cryptographique de 128 bits.",
      "security.s5_title": "Appels Vocaux Chiffrés & Code SAS",
      "security.s5_text": "Balisage sonore en streaming direct AES-256-GCM, authentification vocale par code court SAS anti-interception et coupure garantie sous 4.5s (Dead-Peer Watchdog).",
      "security.s6_title": "Signature Numérique & Anti-Altération",
      "security.s6_text": "Chaque trame et publication est signée numériquement par votre clé privée RSA-2048. L'intégrité du paquet est validée mathématiquement avant exécution pour bloquer toute falsification.",
      "security.s7_title": "Écrasement RAM & Zéro-Trace Volatile",
      "security.s7_text": "Dès leur lecture ou émission, les clés de dérivation éphémères et fragments décodés sont écrasés en mémoire vive via `fill(0)` pour interdire toute extraction forensique.",
      "security.s8_title": "Watchdog Temps Réel & Rupture d'Écoute",
      "security.s8_text": "Un chien de garde actif coupe automatiquement les sessions vocales et détruit les canaux éphémères après 4.5s de silence ou en cas d'anomalie réseau pour garantir l'étanchéité absolue.",
      "security.s9_title": "Moteur IA Vectorisé Hors-Ligne & Zéro Télémétrie",
      "security.s9_text": "Les deux LLM (Guard & Reply) s'exécutent 100% en local via un moteur matriciel Kotlin natif. Aucune donnée d'entraînement, SMS ou empreinte vectorielle ne transite sur le réseau.",

      "help.tag": "GUIDE COMPLET & CENTRE D'AIDE",
      "help.title": "Comment Fonctionne O R B I S ?",
      "help.desc": "Découvrez le guide officiel intégré dans l'application pour maîtriser l'ensemble des flux décentralisés.",
      "help.search_placeholder": "Rechercher un sujet (ex: hors-ligne, vocal, appel, clés, quota, sos)...",
      "help.cat_all": "Tous",
      "help.cat_start": "🚀 Démarrage",
      "help.cat_chat": "💬 Messagerie",
      "help.cat_calls": "📞 Appels",
      "help.cat_social": "👥 Réseau Social",
      "help.cat_ai": "🧠 IA & LLM",
      "help.cat_sec": "🔐 Sécurité",
      "help.cat_sys": "⚙️ Système & GSM",

      "topics.t1_title": "Comment O R B I S fonctionne-t-il à 100% hors-ligne ?",
      "topics.t1_content": "O R B I S n'utilise aucun serveur cloud, API ou passerelle Web. Toutes les communications transitent directement via le réseau téléphonique cellulaire (GSM SMS). Vos messages et publications sont segmentés, compressés et chiffrés avant d'être envoyés directement de smartphone à smartphone.",
      "topics.t2_title": "Comment fonctionne l'appairage et l'échange de clés de sécurité ?",
      "topics.t2_content": "Lors de la première prise de contact, les utilisateurs s'échangent leurs clés publiques de chiffrement soit par QR Code direct (instantané et hors-ondes), soit par SMS d'invitation sécurisé automatique. Une fois la clé validée, un canal chiffré de bout en bout inviolable est établi.",
      "topics.t3_title": "Comment fonctionnent les notes vocales par SMS ?",
      "topics.t3_content": "L'enregistreur audio capture votre voix avec un codec vocal ultra-optimisé (AMR/AAC) et applique une compression Deflater ZLIB. L'application découpe ensuite le fichier audio en fragments SMS compacts et le reconstitue instantanément chez le destinataire avec une barre de lecture interactive (1.0x / 1.5x / 2.0x).",
      "topics.t_call_e2ee_title": "Comment fonctionnent les appels vocaux chiffrés E2EE ?",
      "topics.t_call_e2ee_content": "L'initiation de l'appel utilise une trame SMS de signalisation compacte (Offre ORB:CO:). À l'acceptation (ORB:CA:), un canal de streaming audio direct chiffré en AES-256-GCM est ouvert en peer-to-peer, sans aucun serveur intermédiaire et protégé contre toute interception réseau.",
      "topics.t_call_credit_title": "Que se passe-t-il si un utilisateur n'a pas de crédit SMS lors d'un appel ?",
      "topics.t_call_credit_content": "Si l'appelant n'a pas de crédit SMS, O R B I S détecte immédiatement l'échec et propose de basculer vers un appel GSM standard. Si le destinataire n'a pas de SMS pour émettre la réponse chiffrée, il peut décrocher en mode GSM classique (100% gratuit pour lui en réception), tout en prévenant l'appelant.",
      "topics.t_call_sas_title": "Qu'est-ce que le code SAS pendant l'appel vocal ?",
      "topics.t_call_sas_content": "Le code SAS (Short Authentication String) est dérivé des empreintes cryptographiques de la session et s'affiche sur les deux écrans. En lisant ce court code à voix haute, les deux correspondants vérifient mutuellement et mathématiquement l'absence totale d'espion intermédiaire (MITM) ou de fausses antennes relais.",
      "topics.t_call_hangup_title": "Comment est garanti le raccrochage et la fin d'appel ?",
      "topics.t_call_hangup_content": "Lors de la coupure, O R B I S envoie des paquets UDP de fermeture immédiate, un SMS d'arrêt (ORB:CE:) et active un chien de garde (Dead-Peer Watchdog). En cas de perte soudaine de couverture, la session est automatiquement fermée après 4.5 secondes de silence pour préserver la batterie et la confidentialité.",
      "topics.t4_title": "Le partage GPS nécessite-t-il une connexion Data mobile ?",
      "topics.t4_content": "Non ! La puce GPS satellite de votre smartphone fonctionne sans connexion Internet ni données mobiles. O R B I S lit vos coordonnées satellite exactes et les transmet dans un SMS chiffré de 1 seul segment, ouvrant directement la carte chez votre contact.",
      "topics.t5_title": "Comment fonctionne le mur social et la diffusion exclusivement Entre Amis ?",
      "topics.t5_content": "Le mur social et les stories 24h fonctionnent en réseau P2P décentralisé exclusivement entre amis confirmés et cercles de confiance. Vos publications et sondages ne sont transmis qu'à vos pairs autorisés via GSM P2P, garantissant zéro diffusion intempestive vers vos contacts téléphoniques ordinaires.",
      "topics.t6_title": "Comment la Stéganographie protège-t-elle contre la censure ?",
      "topics.t6_content": "La stéganographie dissimule la charge utile binaire chiffrée sous forme de caractères Unicode invisibles (largeur zéro) insérés dans une phrase d'apparence totalement banale. Pour un opérateur ou un tiers espion, le SMS ressemble à une conversation ordinaire.",
      "topics.t7_title": "Quelle est la consommation réelle de mon forfait SMS ?",
      "topics.t7_content": "Un message texte standard, un emoji de réaction, un partage GPS ou une mise à jour de statut ne consomment que 1 seul SMS. Aucun SMS n'est émis en tâche de fond (0 keep-alive). Sur un forfait avec SMS illimités, l'impact est totalement nul et transparent.",
      "topics.t8_title": "L'application O R B I S est-elle disponible sur iPhone (Apple iOS) ?",
      "topics.t8_content": "Non, O R B I S est une exclusivité stricte Android et ne sera jamais disponible sur iPhone. Le système d'exploitation fermé d'Apple (sandbox iOS) interdit formellement à toute application tierce de remplacer le client SMS de téléphonie par défaut, d'intercepter les trames GSM en arrière-plan ou d'accéder directement au modem cellulaire. Seul l'écosystème ouvert d'Android permet d'offrir une véritable souveraineté de communication 100% décentralisée et hors-ligne.",
      "topics.t_guard_title": "Comment fonctionne ORBIS Guard-LLM contre les arnaques et spams ?",
      "topics.t_guard_content": "ORBIS Guard-LLM évalue chaque SMS classique entrant grâce à des embeddings vectoriels locaux. Il détecte les stratagèmes d'urgence, fausses alertes bancaires, faux numéros SIM et isole les domaines frauduleux (tout en distinguant les abréviations innocentes comme 'M.Pierre'). Les messages douteux sont isolés dans l'onglet 'Spam & bloqués' avec alerte visuelle et option de blocage immédiat.",
      "topics.t_reply_title": "Comment ORBIS Reply-LLM propose-t-il des réponses intelligentes hors-ligne ?",
      "topics.t_reply_content": "ORBIS Reply-LLM analyse le fil de conversation et l'intention du dernier message (salutations, questions, logistique, urgence, remerciements) pour générer 3 pastilles de réponses rapides et polies au-dessus du clavier. L'inférence s'exécute en moins de 10 ms sans aucune connexion Internet ni appel réseau.",
      "topics.t_ai_learning_title": "Comment les modèles s'auto-entraînent-ils sans risque pour ma vie privée ?",
      "topics.t_ai_learning_content": "L'entraînement s'effectue exclusivement sur votre appareil (on-device continual learning). Lorsque vous bloquez un spam ou sélectionnez des suggestions, les poids synaptiques locaux s'adaptent dynamiquement dans le stockage chiffré de l'application. Aucune donnée textuelle, empreinte vectorielle ou statistique n'est envoyée sur un quelconque serveur.",

      "quota.tag": "TRANSPARENCE GSM",
      "quota.title": "Consommation & Sobriété du Quota SMS",
      "quota.desc": "O R B I S compresse chaque paquet à l'octet près pour offrir un ratio coût/résilience optimal.",
      "quota.th_action": "Action Réalisée",
      "quota.th_cost": "Coût SMS",
      "quota.th_details": "Détails Techniques",
      "quota.row1_title": "Message Texte 1 à 1",
      "quota.row1_desc": "Chiffré AES-256-GCM + signature + compression ZLIB",
      "quota.row_call_title": "Signalisation Appel Vocal E2EE",
      "quota.row_call_desc": "Échange des clés de session via SMS GSM • 0 SMS consommé pendant l'appel audio",
      "quota.row2_title": "Partage GPS Satellite",
      "quota.row2_desc": "Coordonnées ultra-compactes ~40 octets",
      "quota.row3_title": "Réaction Emoji / Accusé (ACK)",
      "quota.row3_desc": "Trame d'acquittement allégée et directe",
      "quota.row4_title": "Message de Groupe (N membres)",
      "quota.row4_desc": "Envoi P2P souverain direct à chaque membre du groupe",
      "quota.row5_title": "Note Vocale (3 à 5 secondes)",
      "quota.row5_desc": "Flux audio binaire AMR/AAC compressé en multi-segments",
      "quota.row_ai_title": "Analyse IA Guard-LLM & Reply-LLM",
      "quota.row_ai_desc": "Inférence neuronale 100% CPU locale • 0 octet émis ou reçu • Zéro télémétrie",

      "dl.title": "Téléchargez O R B I S dès Aujourd'hui",
      "dl.desc": "Prenez le contrôle absolu de votre vie privée. Installez l'application souveraine sans passer par les magasins centralisés.",
      "dl.btn_apk": "Télécharger l'APK Officiel",
      "dl.qr_badge": "Scan Rapide",
      "dl.qr_title": "Installation Directe Smartphone",
      "dl.qr_desc": "Scannez ce QR Code avec l'appareil photo de votre smartphone Android pour lancer le téléchargement immédiat.",

      "legal.modal_title": "Confidentialité & Conditions",
      "legal.badge": "100% Hors-Ligne • Souverain",
      "legal.tab_privacy": "Confidentialité",
      "legal.tab_terms": "Conditions & Légal",
      "legal.p1_title": "1. Zéro Collecte & Zéro Serveur",
      "legal.p1_desc": "ORBIS ne dispose d'aucun serveur, base de données centrale ou service d'analyse. Aucune donnée d'utilisation, adresse IP, identifiant ou contact n'est jamais collecté, transmis ou revendu.",
      "legal.p2_title": "2. Chiffrement de Bout en Bout Matériel",
      "legal.p2_desc": "Toutes les communications (messages, notes vocales, publications, sondages) sont chiffrées sur votre appareil avec AES-256-GCM, Double Ratchet (PFS) et RSA-2048 adossés à l'Android KeyStore matériel.",
      "legal.p3_title": "3. Stockage Exclusivement Local",
      "legal.p3_desc": "L'historique de vos discussions, clés privées et publications est conservé localement dans le stockage protégé de l'application. Vous pouvez exporter une archive chiffrée (PBKDF2) ou tout effacer à tout instant.",
      "legal.p4_title": "4. Métadonnées Cellulaires GSM",
      "legal.p4_desc": "Les paquets chiffrés transitent par le réseau SMS de votre opérateur téléphonique. Bien que le contenu soit illisible sans vos clés, votre opérateur conserve les métadonnées de transmission habituelles (numéros, horodatage, antennes-relais) conformément à la réglementation télécoms locale.",
      "legal.t1_title": "1. Licence d'Utilisation & Fourniture « En l'état »",
      "legal.t1_desc": "ORBIS est un logiciel souverain et propriétaire concédé sous licence d'utilisation personnelle, distribué SANS AUCUNE GARANTIE, expresse ou implicite, y compris les garanties d'adéquation à un usage particulier, d'absence d'erreurs ou de disponibilité continue.",
      "legal.t2_title": "2. Responsabilité Exclusive de l'Utilisateur",
      "legal.t2_desc": "Vous reconnaissez être seul responsable de l'utilisation que vous faites de l'application et de vos communications. Vous vous engagez à respecter l'ensemble des lois, réglementations et restrictions en vigueur dans votre juridiction.",
      "legal.t3_title": "3. Décharge de Responsabilité des Développeurs",
      "legal.t3_desc": "Les créateurs, développeurs et contributeurs d'ORBIS déclinent expressément toute responsabilité civile ou pénale quant aux dommages, litiges ou infractions découlant d'une utilisation illicite, frauduleuse ou détournée de l'application.",
      "legal.t4_title": "4. Fonctionnalités d'Urgence (SOS / GPS)",
      "legal.t4_desc": "Les fonctions de partage de position GPS et d'alerte SOS sont des outils d'assistance de pair à pair basés sur le signal radio GSM local et ne se substituent en aucun cas aux services publics d'urgence officiels.",
      "legal.btn_close": "Compris & Accepter",
      "legal.open_modal_btn": "Consulter les Conditions & Confidentialité",
      "topics.t_legal_title": "Politique de Confidentialité & Conditions d'Utilisation",
      "topics.t_legal_content": "ORBIS est une infrastructure souveraine 100% hors-ligne. Aucune donnée n'est collectée ou transmise à des serveurs tiers. Les communications sont chiffrées de bout en bout (AES-256-GCM / RSA-2048 / Double Ratchet). En tant que logiciel propriétaire fourni « en l'état », chaque utilisateur est légalement et exclusivement responsable de son utilisation et de la conformité avec les lois de sa juridiction.",

      "footer.privacy": "Confidentialité",
      "footer.terms": "Conditions & Légal",
      "footer.tagline": "Architecture de communication souveraine, P2P et hors-ligne propulsée par ShaDevPro."
    },

    en: {
      "nav.features": "Features",
      "nav.security": "Security & PFS",
      "nav.help": "Help Center",
      "nav.quota": "GSM Quota",
      "nav.download": "Download",
      "nav.get_app": "Get APK",

      "hero.pill": "Decentralized GSM Protocol • 100% Offline for Android",
      "hero.title_part1": "Communicate Without Internet.",
      "hero.title_part2": "Zero Cloud. Zero Traces.",
      "hero.subtitle": "The world's first sovereign P2P social and encrypted messaging platform powered by GSM SMS network with military-grade AES-256-GCM, Double Ratchet (PFS), and invisible steganography.",
      "hero.download_btn": "Download O R B I S v1.1.0",
      "hero.guide_btn": "Explore Guide & FAQ",
      "hero.telegram_btn": "Telegram Community",
      "dl.btn_telegram": "Telegram Community",
      "dl.btn_github": "Web & Release Repo",
      "dl.checksum_title": "Cryptographic Checksum",
      "dl.copy_btn": "Copy",
      "dl.copied_btn": "✓ Copied!",
      "topics.t_telegram_title": "How to get updates & join the community?",
      "topics.t_telegram_content": "Join our official Telegram community (t.me/orbis_community) to download new signed APK releases directly, receive security alerts, and participate in community discussions and feedback.",

      "badges.encryption_title": "AES-256-GCM & PFS",
      "badges.encryption_sub": "Military Double Ratchet",
      "badges.offline_title": "Zero Cloud Dependency",
      "badges.offline_sub": "Direct GSM SMS P2P",
      "badges.hardware_title": "Android KeyStore",
      "badges.hardware_sub": "Hardware-Backed Security",

      "mockup.peer_name": "Sovereign Mesh Peer",
      "mockup.status": "● E2EE Encrypted • GSM",
      "mockup.msg1": "🔐 Double Ratchet channels initialized successfully. Ephemeral KDF key synchronized.",
      "mockup.msg2": "Great! Encrypted emergency location beacon dispatched in a single SMS.",
      "mockup.gps": "🗺️ GPS: 33.5731° N, 7.5898° W",
      "mockup.msg3": "Steganography active: payload concealed beneath carrier cover text 🛡️",
      "mockup.placeholder": "Encrypted SMS message...",

      "features.tag": "SOVEREIGN PILLARS",
      "features.title": "Digital Autonomy Reinvented",
      "features.desc": "Everything a modern social network and messenger offers, with zero third-party servers and zero internet required.",
      "features.f1_title": "P2P Messaging & Double Ratchet",
      "features.f1_text": "Every message derives a new single-use key via HMAC-SHA256 (PFS). Ephemeral keys are instantly destroyed in memory (`fill(0)`).",
      "features.f2_title": "Voice Notes & Interactive Waveform",
      "features.f2_text": "Ultra-dense ZLIB compression, touch scrubbing, and smooth playback speed switching (1.0x / 1.5x / 2.0x).",
      "features.f3_title": "GPS Sharing & SOS Emergency Widget",
      "features.f3_text": "Broadcast distress alerts with satellite coordinates to trusted circles in 1 tap directly from the Android home screen.",
      "features.f4_title": "P2P Social Feed & 24h Stories (Friends-Only)",
      "features.f4_text": "Share posts, decentralized polls, and ephemeral stories exclusively with confirmed friends and trusted circles without ordinary contact leakage.",
      "features.f7_title": "Encrypted E2EE Voice Calls",
      "features.f7_text": "Offline GSM SMS signaling, direct AES-256-GCM audio streaming, SAS security code verification, and automatic fallback to standard GSM cellular voice when SMS credits are depleted.",
      "features.f5_title": "Invisible SMS Steganography",
      "features.f5_text": "Camouflages encrypted binary payloads as zero-width invisible Unicode characters inside ordinary plain text.",
      "features.f6_title": "V3 Encrypted Vault & P2P Migration",
      "features.f6_text": "Full PBKDF2 (100k iterations) backup, smart storage cleaner, and phone-to-phone migration without the cloud.",
      "features.f8_title": "Smart OTP Extraction & Spam Filter",
      "features.f8_text": "Instant on-device detection and 1-tap copy of 2FA/banking codes (Google, WhatsApp, banks) with top priority sorting and local spam filtering with zero internet.",
      "features.f9_title": "Dual-SIM & Native GSM Routing",
      "features.f9_text": "Native Android Dual-SIM support with automatic carrier signal routing and strict SMS quota accounting per cellular SIM slot.",
      "features.f_guard_title": "ORBIS Guard-LLM: Anti-Scam Shield",
      "features.f_guard_text": "Real-time vectorized semantic SMS analysis. Instant detection of fake banking URLs, smishing, and scams with tri-state classification (Safe, Suspicious, Danger) and 1-tap blocking.",
      "features.f_reply_title": "ORBIS Reply-LLM: Smart Contextual Replies",
      "features.f_reply_text": "Instant on-device generation of 3 polite, relevant suggestion chips above the composer in encrypted chats and standard SMS across French, English, and Arabic.",
      "features.f_ai_engine_title": "Vectorized Neural Engine & Continual Learning",
      "features.f_ai_engine_text": "Proprietary 100% native Kotlin neural models (< 2MB, < 10ms inference). Dynamic on-device continual training from user feedback with zero cloud servers and zero telemetry.",

      "security.tag": "FORTRESS ARCHITECTURE",
      "security.title": "Deep Multi-Layer Defense",
      "security.desc": "Every layer of O R B I S is hardened to resist reverse-engineering, network eavesdropping, and physical extraction.",
      "security.s1_title": "RSA-2048 Asymmetric Cryptography",
      "security.s1_text": "Private/public key pairs generated on-device. Cryptographic signing of every packet guarantees authenticity.",
      "security.s2_title": "Double Ratchet & Perfect Forward Secrecy",
      "security.s2_text": "Automatic ratchet renewal of session keys. Past and future conversations remain unbreakable even after key theft.",
      "security.s3_title": "At-Rest Storage via Android KeyStore",
      "security.s3_text": "Local databases and app files are sealed by a hardware AES-GCM master key managed in the TEE enclave.",
      "security.s4_title": "PBKDF2 100k Iterations Vault Derivation",
      "security.s4_text": "Your `.orbis` vault archives resist brute-force attacks thanks to slow PBKDF2 hashing and a 128-bit random salt.",
      "security.s5_title": "Encrypted Voice Calls & SAS Code",
      "security.s5_text": "Direct AES-256-GCM audio streaming, verbal SAS verification against MITM interception, and guaranteed termination under 4.5s (Dead-Peer Watchdog).",
      "security.s6_title": "Digital Signatures & Anti-Tamper",
      "security.s6_text": "Every packet, call frame, and post is cryptographically signed using your on-device RSA-2048 private key, preventing spoofing and message tampering.",
      "security.s7_title": "Volatile RAM Zeroization & Zero-Trace",
      "security.s7_text": "Ephemeral ratchet keys and decrypted buffers are overwritten in RAM immediately after use (`fill(0)`), rendering cold-boot memory dump attacks ineffective.",
      "security.s8_title": "Real-Time Watchdog & Anti-Wiretap",
      "security.s8_text": "Active Dead-Peer Watchdog terminates sessions and purges crypto sockets after 4.5 seconds of network anomaly or silence, preventing hanging connections.",
      "security.s9_title": "Offline Vectorized AI Engine & Zero Telemetry",
      "security.s9_text": "Both LLMs (Guard & Reply) run 100% locally on CPU via a native Kotlin matrix engine. Training data, private SMS text, and vector embeddings never leave your device.",

      "help.tag": "COMPREHENSIVE GUIDE & HELP CENTER",
      "help.title": "How Does O R B I S Work?",
      "help.desc": "Discover the official in-app guide to master all decentralized and offline communication flows.",
      "help.search_placeholder": "Search topics (e.g. offline, voice, call, keys, quota, sos)...",
      "help.cat_all": "All",
      "help.cat_start": "🚀 Getting Started",
      "help.cat_chat": "💬 Messaging",
      "help.cat_calls": "📞 Voice Calls",
      "help.cat_social": "👥 Social Network",
      "help.cat_ai": "🧠 AI & LLM",
      "help.cat_sec": "🔐 Security",
      "help.cat_sys": "⚙️ System & GSM",

      "topics.t1_title": "How does O R B I S operate 100% offline?",
      "topics.t1_content": "O R B I S uses zero cloud servers, APIs, or web relays. All communications flow directly over the cellular network (GSM SMS). Messages and posts are chunked, compressed, and encrypted before direct peer-to-peer transmission.",
      "topics.t2_title": "How does key pairing and exchange work?",
      "topics.t2_content": "On first contact, peers exchange public keys via offline QR Code scan or automated secure SMS handshake. Once verified, an impenetrable end-to-end encrypted channel is created.",
      "topics.t3_title": "How do voice notes over SMS work?",
      "topics.t3_content": "The audio recorder captures voice in high compression (AMR/AAC) and applies Deflater ZLIB. O R B I S chunks the binary audio into SMS fragments, reconstructed instantly on playback.",
      "topics.t_call_e2ee_title": "How do E2EE encrypted voice calls work?",
      "topics.t_call_e2ee_content": "Call initiation uses an ultra-compact signaling SMS frame (Offer ORB:CO:). Upon accept (ORB:CA:), a direct peer-to-peer audio stream encrypted with AES-256-GCM is opened without intermediary servers, immune to eavesdropping.",
      "topics.t_call_credit_title": "What happens if a user has 0 SMS credit during a call?",
      "topics.t_call_credit_content": "If the caller has no SMS credit, O R B I S instantly detects delivery failure and offers fallback to a standard GSM call. If the receiver has 0 SMS to transmit the answer key, they can answer as a standard cellular GSM call (100% free for them to receive) while alerting the caller.",
      "topics.t_call_sas_title": "What is the SAS security code during a voice call?",
      "topics.t_call_sas_content": "The Short Authentication String (SAS) code is derived from session crypto fingerprints and shown on both screens. Reading this short code aloud mathematically confirms zero intermediary wiretapping (MITM) or rogue cellular towers.",
      "topics.t_call_hangup_title": "How is reliable hangup and call termination guaranteed?",
      "topics.t_call_hangup_content": "Upon ending a call, O R B I S transmits rapid UDP termination bursts, a teardown SMS (ORB:CE:), and activates a Dead-Peer watchdog. In case of network drop, the session terminates automatically after 4.5 seconds of silence to preserve battery and privacy.",
      "topics.t4_title": "Does GPS location sharing require mobile data?",
      "topics.t4_content": "No! Satellite GPS hardware works without mobile internet or data SIMs. O R B I S extracts coordinates and transmits them in a single encrypted SMS opening maps directly.",
      "topics.t5_title": "How does the Friends-Only social feed operate?",
      "topics.t5_content": "The social wall and 24h stories operate in a decentralized P2P network strictly between confirmed friends and trusted circles. Your posts and polls are sent only to authorized peers via GSM P2P with zero spam to ordinary phone contacts.",
      "topics.t6_title": "How does Steganography protect against censorship?",
      "topics.t6_content": "Steganography conceals encrypted payloads into zero-width invisible Unicode characters inside innocuous sentences, looking like normal SMS to carriers.",
      "topics.t7_title": "What is the actual GSM SMS quota consumption?",
      "topics.t7_content": "Standard text, reactions, GPS shares, or status updates take only 1 single SMS. Zero background keep-alive messages. On unlimited SMS plans, impact is completely zero.",
      "topics.t8_title": "Is O R B I S available on iPhone (Apple iOS)?",
      "topics.t8_content": "No, O R B I S is strictly exclusive to Android and will never be available on iPhone. Apple's closed iOS sandbox explicitly prohibits third-party apps from acting as the default SMS telephony client, intercepting raw GSM SMS frames in the background, or directly controlling cellular modem hardware. Only Android's open architecture enables true, decentralized off-grid digital sovereignty.",
      "topics.t_guard_title": "How does ORBIS Guard-LLM detect scams and spam?",
      "topics.t_guard_content": "ORBIS Guard-LLM analyzes inbound standard SMS using local vector embeddings. It spots urgency pressure tactics, spoofed bank warnings, fraudulent SIM alerts, and phishing domains (while correctly ignoring normal abbreviations like 'M.Pierre'). Suspicious texts are safely isolated in the 'Spam & Blocked' tab with risk warnings and 1-tap blocking.",
      "topics.t_reply_title": "How does ORBIS Reply-LLM generate smart replies offline?",
      "topics.t_reply_content": "ORBIS Reply-LLM analyzes conversation context and intent (greetings, questions, confirmations, urgency, gratitude) to generate 3 actionable suggestion chips above the keyboard. Inference executes in under 10ms with zero internet connection or background requests.",
      "topics.t_ai_learning_title": "How do the AI models train without compromising privacy?",
      "topics.t_ai_learning_content": "Model adaptation happens exclusively on your device (on-device continual learning). When you block a spam message or pick reply suggestions, local neural weights adapt dynamically within the app's encrypted storage. No text, vector weights, or statistics are ever transmitted to any remote server.",

      "quota.tag": "GSM TRANSPARENCY",
      "quota.title": "SMS Quota Consumption & Efficiency",
      "quota.desc": "O R B I S compresses every packet to the byte to deliver maximum resilience and cost efficiency.",
      "quota.th_action": "Action Performed",
      "quota.th_cost": "SMS Cost",
      "quota.th_details": "Technical Details",
      "quota.row1_title": "1-on-1 Text Message",
      "quota.row1_desc": "AES-256-GCM + signature + ZLIB compression",
      "quota.row_call_title": "E2EE Voice Call Signaling",
      "quota.row_call_desc": "Session key exchange via GSM SMS • 0 SMS consumed during audio stream conversation",
      "quota.row2_title": "Satellite GPS Sharing",
      "quota.row2_desc": "Ultra-compact coordinates ~40 bytes",
      "quota.row3_title": "Emoji Reaction / ACK",
      "quota.row3_desc": "Lightweight single-frame ACK packet",
      "quota.row4_title": "Group Message (N members)",
      "quota.row4_desc": "Direct P2P delivery to each circle member",
      "quota.row5_title": "Voice Note (3 to 5 seconds)",
      "quota.row5_desc": "Binary audio compressed in multi-part fragments",
      "quota.row_ai_title": "AI Guard-LLM & Reply-LLM Analysis",
      "quota.row_ai_desc": "100% On-Device CPU inference • 0 bytes sent or received • Zero telemetry",

      "dl.title": "Download O R B I S Today",
      "dl.desc": "Take absolute control over your digital privacy. Install the sovereign app directly without centralized stores.",
      "dl.btn_apk": "Download Official APK",
      "dl.qr_badge": "Quick Scan",
      "dl.qr_title": "Direct Mobile Install",
      "dl.qr_desc": "Scan this QR Code with your Android camera to start instant download.",

      "legal.modal_title": "Privacy & Terms of Service",
      "legal.badge": "100% Offline • Sovereign",
      "legal.tab_privacy": "Privacy Policy",
      "legal.tab_terms": "Terms of Use",
      "legal.p1_title": "1. Zero Data Collection & Zero Cloud",
      "legal.p1_desc": "ORBIS has no central servers, analytics or databases. No personal data, IP addresses, identifiers, or contacts are ever collected, transmitted, or sold.",
      "legal.p2_title": "2. Hardware-Backed End-to-End Encryption",
      "legal.p2_desc": "All communications (messages, voice notes, posts, polls) are encrypted on-device with AES-256-GCM, Double Ratchet (PFS), and RSA-2048 backed by Android KeyStore hardware.",
      "legal.p3_title": "3. Exclusively On-Device Storage",
      "legal.p3_desc": "Your chat history, private keys, and posts are stored locally in the app's secure vault. You can export a PBKDF2-encrypted backup or wipe everything at any time.",
      "legal.p4_title": "4. Cellular GSM Transport Metadata",
      "legal.p4_desc": "Encrypted packets are transmitted via your mobile carrier's SMS network. While message content is cryptographically sealed, your mobile operator logs standard routing metadata (phone numbers, timestamps, cell towers) according to local telecom regulations.",
      "legal.t1_title": "1. End-User License & 'As-Is' Provision",
      "legal.t1_desc": "ORBIS is sovereign and proprietary software provided under a personal use license, distributed WITHOUT ANY WARRANTY, express or implied, including merchantability, fitness for a particular purpose, or error-free operation.",
      "legal.t2_title": "2. User Legal Responsibility & Compliance",
      "legal.t2_desc": "You acknowledge that you are solely responsible for your use of the application and all transmissions. You agree to comply with all applicable laws, regulations, and restrictions in your jurisdiction.",
      "legal.t3_title": "3. Developer Limitation of Liability",
      "legal.t3_desc": "The creators, developers, and contributors of ORBIS expressly disclaim any liability for any damages, losses, claims, or unlawful activities resulting from the misuse or deployment of this software.",
      "legal.t4_title": "4. Emergency Features (SOS / GPS)",
      "legal.t4_desc": "GPS location sharing and SOS alert features are peer-to-peer assistance tools based on local GSM radio coverage and do not replace official public emergency services.",
      "legal.btn_close": "Understood & Accept",
      "legal.open_modal_btn": "View Terms & Privacy Policy",
      "topics.t_legal_title": "Privacy Policy & Terms of Service",
      "topics.t_legal_content": "ORBIS is a 100% offline sovereign infrastructure. No personal data is collected or sent to third-party servers. All communications are end-to-end encrypted (AES-256-GCM / RSA-2048 / Double Ratchet). As proprietary software provided 'as is', each user remains solely and legally responsible for their usage and compliance with local laws.",

      "footer.privacy": "Privacy Policy",
      "footer.terms": "Terms of Use",
      "footer.tagline": "Sovereign, P2P, and offline communication architecture powered by ShaDevPro."
    },

    ar: {
      "nav.features": "المميزات",
      "nav.security": "الأمان و PFS",
      "nav.help": "مركز المساعدة",
      "nav.quota": "استهلاك الرسائل",
      "nav.download": "تحميل التطبيق",
      "nav.get_app": "تحميل APK",

      "hero.pill": "بروتوكول لامركزي GSM • يعمل 100% بدون إنترنت لنظام أندرويد",
      "hero.title_part1": "تواصل بلا إنترنت.",
      "hero.title_part2": "بلا خوادم. بلا أثر.",
      "hero.subtitle": "أول منصة تواصل اجتماعي ومراسلة سيادية من نظير إلى نظير (P2P) في العالم تعمل عبر شبكة الرسائل القصيرة SMS مع تشفير عسكري AES-256-GCM وبروتوكول Double Ratchet وإخفاء البيانات غير المرئي.",
      "hero.download_btn": "تحميل O R B I S v1.1.0",
      "hero.guide_btn": "دليل الاستخدام والأسئلة الشائعة",
      "hero.telegram_btn": "مجتمع تيليجرام الرسمي",
      "dl.btn_telegram": "مجتمع تيليجرام",
      "dl.btn_github": "مستودع الموقع والإصدارات",
      "dl.checksum_title": "البصمة الرقمية للتحقق",
      "dl.copy_btn": "نسخ",
      "dl.copied_btn": "✓ تم النسخ!",
      "topics.t_telegram_title": "كيفية الحصول على التحديثات والانضمام إلى المجتمع؟",
      "topics.t_telegram_content": "انضم إلى مجتمعنا الرسمي على تيليجرام (t.me/orbis_community) لتحميل إصدارات APK الجديدة الموقعة مباشرة، وتلقي تنبيهات الأمان والمشاركة في نقاشات المجتمع وتقديم آرائكم.",

      "badges.encryption_title": "تشفير عسكري AES-256",
      "badges.encryption_sub": "بروتوكول Double Ratchet PFS",
      "badges.offline_title": "استقلال تام عن السحابة",
      "badges.offline_sub": "اتصال مباشر P2P عبر GSM",
      "badges.hardware_title": "حماية عتادية KeyStore",
      "badges.hardware_sub": "حماية مدمجة بالمعالج",

      "mockup.peer_name": "نظير شبكة سيادية (Mesh)",
      "mockup.status": "● مشفر طرفاً لطرف • GSM",
      "mockup.msg1": "🔐 تم تهيئة قنوات Double Ratchet بنجاح. تمت مزامنة مفتاح KDF المؤقت.",
      "mockup.msg2": "ممتاز! تم إرسال إحداثيات الطوارئ المشفرة في رسالة SMS واحدة.",
      "mockup.gps": "🗺️ GPS: 33.5731° N, 7.5898° W",
      "mockup.msg3": "إخفاء البيانات نشط: الحمولة المشفرة مخفية تحت نص حامل عادي 🛡️",
      "mockup.placeholder": "رسالة SMS مشفرة...",

      "features.tag": "الأركان السيادية",
      "features.title": "إعادة ابتكار الاستقلالية الرقمية",
      "features.desc": "كل ما تقدمه شبكات التواصل والمراسلة الحديثة، دون الحاجة لأي خادم وسيط أو اتصال بالإنترنت.",
      "features.f1_title": "مراسلة P2P مع Double Ratchet",
      "features.f1_text": "توليد مفتاح جلسة جديد لكل رسالة عبر HMAC-SHA256 مع إتلاف فوري للمفاتيح في الذاكرة بعد القراءة (`fill(0)`).",
      "features.f2_title": "رسائل صوتية ومخطط تفاعلي",
      "features.f2_text": "ضغط فائق ZLIB، تصفح دقيق باللمس مع خيارات سرعة تشغيل ديناميكية (1.0x / 1.5x / 2.0x).",
      "features.f3_title": "مشاركة الموقع وزر الطوارئ SOS",
      "features.f3_text": "إرسال نداء استغاثة مع إحداثيات GPS المباشرة لدوائر الثقة بنقرة واحدة من الشاشة الرئيسية.",
      "features.f4_title": "جدار اجتماعي وقصص 24 ساعة حصرياً بين الأصدقاء",
      "features.f4_text": "شارك المنشورات واستطلاعات الرأي والقصص المؤقتة حصرياً مع أصدقائك المؤكدين ودوائر الثقة دون تسريب لجهات اتصال هاتفك الأخرى.",
      "features.f7_title": "مكالمات صوتية مشفرة E2EE",
      "features.f7_text": "إشارات خلوية عبر SMS بدون إنترنت، بث صوتي مباشر مشفر بـ AES-256-GCM، توثيق برمز SAS وتحويل مجاني لمكالمات GSM عند انعدام رصيد الرسائل.",
      "features.f5_title": "إخفاء البيانات غير المرئي (Steganography)",
      "features.f5_text": "إخفاء الحزم المشفرة داخل نصوص عادية باستخدام أحرف يونيكود خفية عديمة العرض لتفادي الرقابة.",
      "features.f6_title": "خزنة مشفرة V3 وترحيل مباشر",
      "features.f6_text": "نسخ احتياطي فائق الأمان عبر PBKDF2 (100 ألف دورة)، تنظيف تلقائي وترحيل البيانات بين الهواتف بدون سحابة.",
      "features.f8_title": "استخراج رموز OTP وتصفية ذكية",
      "features.f8_text": "اكتشاف فوري ونسخ بنقرة واحدة لرموز التحقق الثنائي والبنوك مع أولوية في أعلى القائمة وتصفية محلية للرسائل المزعجة بدون إنترنت.",
      "features.f9_title": "إدارة الشرائح المتعددة وتوجيه GSM",
      "features.f9_text": "دعم أصلي للهواتف ثنائية الشريحة مع تبديل تلقائي حسب جودة التغطية الخلوية ومتابعة دقيقة لاستهلاك الرسائل لكل خط.",
      "features.f_guard_title": "ORBIS Guard-LLM : درع مكافحة الاحتيال",
      "features.f_guard_text": "تحليل دلالي متجهي فوري لرسائل SMS. اكتشاف الروابط البنكية المزيفة والاحتيال وتصنيف ثلاثي الأبعاد (آمن، مشبوه، خطير) مع حظر فوري بنقرة واحدة.",
      "features.f_reply_title": "ORBIS Reply-LLM : اقتراحات الرد الذكية",
      "features.f_reply_text": "توليد فوري لـ 3 ردود سريعة ومهذبة ومطابقة للسياق أعلى لوحة الكتابة في المحادثات المشفرة والرسائل العادية باللغات الفرنسية والإنجليزية والعربية.",
      "features.f_ai_engine_title": "محرك ذكاء اصطناعي متجهي وتعلّم ذاتي",
      "features.f_ai_engine_text": "نماذج عصبية سيادية بلغة كوتلن الأصلية 100% (أقل من 2 ميغابايت وسرعة استنتاج أقل من 10 مللي ثانية). تعلّم مستمر محلياً من تفاعلات المستخدم بدون أي خوادم أو تتبع.",

      "security.tag": "بنية الحصن الرقمي",
      "security.title": "أمان متعدد الطبقات في العمق",
      "security.desc": "تم تصميم وتدقيق كل طبقة في O R B I S لمقاومة الهندسة العكسية والتنصت واستخراج البيانات المادي.",
      "security.s1_title": "تشفير غير متناظر RSA-2048",
      "security.s1_text": "توليد مفاتيح التشفير محلياً على الهاتف وتوقيع رقمي لكل حزمة لضمان هوية المرسل بدقة.",
      "security.s2_title": "بروتوكول Double Ratchet وسرية مستقبلية",
      "security.s2_text": "تجديد تلقائي وغير قابل للعكس لمفاتيح الجلسة، مما يجعل المحادثات السابقة والمستقبلية غير قابلة للاختراق.",
      "security.s3_title": "تخزين محمي عبر Android KeyStore",
      "security.s3_text": "حماية قواعد البيانات والملفات المحلية بمفتاح رئيسي عتادي داخل بيئة المعالج الآمنة TEE.",
      "security.s4_title": "اشتقاق الخزنة عبر PBKDF2",
      "security.s4_text": "حماية الأرشيفات ضد هجمات التخمين بفضل 100 ألف تكرار هاش وملح عشوائي 128 بت.",
      "security.s5_title": "مكالمات صوتية مشفرة ورمز SAS",
      "security.s5_text": "بث صوتي مباشر مشفر بتقنية AES-256-GCM، مطابقة شفهية لرمز SAS لمنع التنصت وضمان إنهاء المكالمة خلال 4.5 ثوانٍ.",
      "security.s6_title": "التوقيع الرقمي ومكافحة التلاعب",
      "security.s6_text": "يتم توقيع كل حزمة ورسالة رقمياً بمفتاح RSA-2048 الخاص بك محلياً، مع التحقق الحسابي من سلامة البيانات لمنع أي تزوير أو تعديل.",
      "security.s7_title": "إتلاف الذاكرة المؤقتة وانعدام الأثر",
      "security.s7_text": "تدمير فوري للمفاتيح المؤقتة والبيانات المفكوكة في ذاكرة RAM بمجرد قراءتها (`fill(0)`) للحماية ضد هجمات تفريغ الذاكرة والتحليل الجنائي.",
      "security.s8_title": "حارس الجلسات وقطع الاتصال الفوري",
      "security.s8_text": "مراقب آلي نشط يغلق جلسات الاتصال ويدمر القنوات المشفرة بعد 4.5 ثوانٍ من انقطاع الإشارة لمنع أي ثغرات أو تنصت غير مباشر.",
      "security.s9_title": "محرك ذكاء اصطناعي متجهي بدون إنترنت وانعدام التتبع",
      "security.s9_text": "يعمل كِلا النموذجين (Guard و Reply) بنسبة 100% محلياً على المعالج عبر محرك مصفوفات كوتلن أصلي. لا يتم إرسال أي نصوص أو أوزان عصبية أو بيانات تدريب عبر الشبكة مطلقاً.",

      "help.tag": "دليل الاستخدام ومركز المعرفة",
      "help.title": "كيف يعمل تطبيق O R B I S؟",
      "help.desc": "اكتشف الدليل الرسمي المدمج داخل التطبيق للتحكم بجميع مسارات التواصل اللامركزي.",
      "help.search_placeholder": "ابحث في المواضيع (مثال: بدون نت، مكالمات، صوتي، مفاتيح، رسائل، طوارئ)...",
      "help.cat_all": "الكل",
      "help.cat_start": "🚀 البداية",
      "help.cat_chat": "💬 المراسلة",
      "help.cat_calls": "📞 المكالمات",
      "help.cat_social": "👥 التواصل الاجتماعي",
      "help.cat_ai": "🧠 الذكاء الاصطناعي",
      "help.cat_sec": "🔐 الأمان",
      "help.cat_sys": "⚙️ النظام و GSM",

      "topics.t1_title": "كيف يعمل O R B I S بدون إنترنت بنسبة 100%؟",
      "topics.t1_content": "لا يستخدم O R B I S أي خوادم سحابية أو واجهات ويب. تتم جميع الاتصالات عبر شبكة الهواتف المحمولة التقليدية (GSM SMS). يتم تقسيم الرسائل وضغطها وتشفيرها لتنتقل مباشرة من هاتف إلى هاتف.",
      "topics.t2_title": "كيف يتم تبادل مفاتيح الأمان والاقتران؟",
      "topics.t2_content": "عند أول تواصل، يتبادل المستخدمون المفاتيح العامة إما بمسح رمز QR المباشر بدون شبكة، أو عبر رسالة SMS ترحيبية مشفرة. بمجرد التوثيق، يتم فتح قناة آمنة تماماً.",
      "topics.t3_title": "كيف تعمل الرسائل الصوتية عبر SMS؟",
      "topics.t3_content": "يقوم المسجل بالتقاط الصوت بترميز عالي الكفاءة (AMR/AAC) وضغطه عبر ZLIB. يوزع التطبيق الملف الصوتي على أجزاء SMS ويعيد تجميعه فوراً عند المستلم مع شريط تحكم متقدم.",
      "topics.t_call_e2ee_title": "كيف تعمل المكالمات الصوتية المشفرة (E2EE)؟",
      "topics.t_call_e2ee_content": "يقوم O R B I S ببدء المكالمة الصوتية عبر رسالة SMS مضغوطة للغاية (عرض الاتصال ORB:CO:). عند قبول الطرف الآخر (ORB:CA:)، يتم إنشاء بث صوتي مباشر ومشفر بـ AES-256-GCM بدون خوادم وسيطة ومع حماية تامة ضد التنصت.",
      "topics.t_call_credit_title": "ماذا يحدث عند انعدام رصيد رسائل SMS أثناء الاتصال؟",
      "topics.t_call_credit_content": "إذا نفد رصيد المتصل، يكتشف O R B I S فشل الإرسال فورياً ويعرض التحويل لاتصال GSM عادي. وإذا كان رصيد المستلم منعدماً لإرسال مفتاح الرد، يمكنه استقبال المكالمة كخط GSM قياسي (مجاني 100% له) مع إشعار المتصل فورياً.",
      "topics.t_call_sas_title": "ما هو رمز الأمان SAS أثناء المكالمة الصوتية؟",
      "topics.t_call_sas_content": "رمز SAS هو كود مشتق فريد يظهر أعلى شاشة الاتصال. بمجرد قراءته ومقارنته صوتياً مع الطرف الآخر، يتأكد كلا الطرفين رياضياً من استحالة وجود أي وسيط متنصت أو أبراج خلوية مزيفة.",
      "topics.t_call_hangup_title": "كيف يعمل إنهاء المكالمة وضمان إغلاق الخط؟",
      "topics.t_call_hangup_content": "عند الضغط على إنهاء المكالمة، يرسل O R B I S حزم إغلاق فورية عبر مسار مخصص، ويرسل رسالة إنهاء SMS (ORB:CE:) مع تفعيل مراقب الاتصال. في حال انقطاع التغطية، يتم إنهاء الجلسة تلقائياً خلال 4.5 ثانية لحماية البطارية والخصوصية.",
      "topics.t4_title": "هل تتطلب مشاركة الموقع GPS بيانات الهاتف؟",
      "topics.t4_content": "كلا! تعمل شريحة الأقمار الصناعية GPS بدون أي إنترنت. يقرأ التطبيق الإحداثيات ويرسلها مشفرة في رسالة قصيرة واحدة فقط لتفتح الخريطة مباشرة عند الطرف الآخر.",
      "topics.t5_title": "كيف تعمل الشبكة الاجتماعية والقصص حصرياً بين الأصدقاء؟",
      "topics.t5_content": "يعمل الحائط الاجتماعي والقصص لمدة 24 ساعة مباشرة بين الهواتف (P2P) حصرياً بين الأصدقاء المؤكدين ودوائر الثقة. تُرسل منشوراتك واستطلاعات الرأي فقط لأصدقائك المقبولين عبر GSM P2P دون تخزين مركزي أو إزعاج لجهات اتصال هاتفك الأخرى.",
      "topics.t6_title": "كيف تحمي تقنية الإخفاء (Steganography) من الرقابة؟",
      "topics.t6_content": "تقوم التقنية بإخفاء الحزمة المشفرة داخل جملة عادية جداً كأحرف غير مرئية. تبدو الرسالة لشركات الاتصالات أو المراقبين كحديث يومي عادي.",
      "topics.t7_title": "كم يستهلك التطبيق من رصيد الرسائل القصيرة؟",
      "topics.t7_content": "الرسائل النصية، التفاعلات، مشاركة الموقع وتحديثات الحالة تستهلك رسالة SMS واحدة فقط. لا توجد أي رسائل خفية في الخلفية. مع الباقات غير المحدودة يكون التأثير معدوماً تماماً.",
      "topics.t8_title": "هل يتوفر تطبيق O R B I S على هواتف آيفون (Apple iOS)؟",
      "topics.t8_content": "كلا، تطبيق O R B I S حصري تماماً لنظام أندرويد ولن يتوفر أبداً على هواتف آيفون. يمنع نظام آبل المغلق (iOS Sandbox) بشكل قاطع أي تطبيق خارجي من أن يكون المعالج الافتراضي للرسائل القصيرة، أو التقاط رسائل GSM بالخلفية، أو التحكم بمودم الاتصال. بنية أندرويد المفتوحة هي الوحيدة التي تتيح سيادة رقمية حقيقية وتواصلاً مشفراً بدون إنترنت.",
      "topics.t_guard_title": "كيف يحمي ORBIS Guard-LLM من رسائل الاحتيال والسبام؟",
      "topics.t_guard_content": "يقوم ORBIS Guard-LLM بفحص كل رسالة SMS عادية واردة باستخدام تضمينات متجهية محلية. يكتشف أساليب الضغط والاستعجال، التنبيهات البنكية المزيفة، ويفصل الروابط الخبيثة (مع التمييز الدقيق للاختصارات العادية مثل 'M.Pierre'). تُعزل الرسائل المشبوهة في تبويب 'الرسائل غير المرغوب فيها والمحظورة' مع تحذيرات وخيار حظر فوري.",
      "topics.t_reply_title": "كيف يولد ORBIS Reply-LLM ردوداً ذكية بدون إنترنت؟",
      "topics.t_reply_content": "يحلل ORBIS Reply-LLM سياق المحادثة ومقصد الرسالة الأخيرة (تحيات، أسئلة، مواعيد، طوارئ، شكر) ليقدم 3 اقتراحات ردود سريعة ومناسبة أعلى لوحة المفاتيح. تتم المعالجة خلال أقل من 10 مللي ثانية دون أي اتصال بالإنترنت أو استهلاك للبيانات.",
      "topics.t_ai_learning_title": "كيف تتعلم النماذج ذاتياً دون انتهاك الخصوصية؟",
      "topics.t_ai_learning_content": "يتم التدريب والتحديث حصرياً على جهازك (on-device continual learning). عند حظر رسالة مزعجة أو اختيار ردود مقترحة، تتكيف الأوزان العصبية محلياً داخل وحدة التخزين المشفرة للتطبيق. لا يتم إرسال أي بيانات نصية أو بصمات رقمية أو إحصائيات إلى أي خادم خارجي نهائياً.",

      "quota.tag": "الشفافية في استهلاك الشبكة",
      "quota.title": "استهلاك باقة الرسائل القصيرة",
      "quota.desc": "يضغط O R B I S كل حزمة بأعلى كفاءة لتوفير استهلاك الرصيد مع أقصى درجات الحماية.",
      "quota.th_action": "العملية",
      "quota.th_cost": "التكلفة",
      "quota.th_details": "التفاصيل التقنية",
      "quota.row1_title": "رسالة نصية مباشرة 1 إلى 1",
      "quota.row1_desc": "تشفير AES-256 + توقيع + ضغط ZLIB",
      "quota.row_call_title": "إشارة مكالمة صوتية E2EE",
      "quota.row_call_desc": "تبادل مفاتيح الجلسة عبر GSM SMS • صفر رسائل مستهلكة أثناء المكالمة الصوتية",
      "quota.row2_title": "مشاركة موقع GPS فضائي",
      "quota.row2_desc": "إحداثيات فائقة الصغر ~40 بايت",
      "quota.row3_title": "تفاعل إيموجي / إشعار استلام",
      "quota.row3_desc": "حزمة إشعار استلام مفردة وخفيفة",
      "quota.row4_title": "رسالة جماعية (N أعضاء)",
      "quota.row4_desc": "إرسال مباشر P2P لكل عضو في المجموعة",
      "quota.row5_title": "رسالة صوتية (3 إلى 5 ثوانٍ)",
      "quota.row5_desc": "ملف صوتي ثنائي مضغوط متعدد الأجزاء",
      "quota.row_ai_title": "تحليل الذكاء الاصطناعي Guard-LLM و Reply-LLM",
      "quota.row_ai_desc": "معالجة محلية 100% على معالج الهاتف • صفر بايت مرسل أو مستقبل • انعدام تام لأي تتبع",

      "dl.title": "حمّل تطبيق O R B I S الآن",
      "dl.desc": "امتلك سيادتك الرقمية وخصوصيتك المطلقة. ثبت التطبيق مباشرة دون الاعتماد على المتاجر المركزية.",
      "dl.btn_apk": "تحميل ملف APK الرسمي",
      "dl.qr_badge": "مسح سريع",
      "dl.qr_title": "تثبيت مباشر على الهاتف",
      "dl.qr_desc": "امسح رمز الاستجابة السريعة بكاميرا هاتفك لبدء التحميل الفوري.",

      "legal.modal_title": "الخصوصية والشروط القانونية",
      "legal.badge": "100% بدون إنترنت • سيادي ومشفر",
      "legal.tab_privacy": "الخصوصية",
      "legal.tab_terms": "الشروط والأحكام",
      "legal.p1_title": "1. انعدام جمع البيانات والخوادم السحابية",
      "legal.p1_desc": "لا يملك أوربيس أي خوادم مركزية أو تحليلات. لا يتم جمع أو نقل أو بيع أي بيانات شخصية أو عناوين IP أو جهات اتصال نهائياً.",
      "legal.p2_title": "2. تشفير شامل طرفاً لطرف معتمد عتادياً",
      "legal.p2_desc": "جميع المراسلات (رسائل، تسجيلات صوتية، منشورات، استطلاعات) مشفرة على جهازك بتقنيات AES-256-GCM و Double Ratchet و RSA-2048 المحمية عتادياً.",
      "legal.p3_title": "3. تخزين محلي حصري على الجهاز",
      "legal.p3_desc": "يتم تخزين سجل المحادثات والمفاتيح الخاصة والمنشورات محلياً في خزانة التطبيق الآمنة. يمكنك تصدير نسخة احتياطية مشفرة بـ PBKDF2 أو مسح كل شيء في أي لحظة.",
      "legal.p4_title": "4. بيانات الإرسال الوصفية لشبكة GSM",
      "legal.p4_desc": "تنتقل الحزم المشفرة عبر شبكة SMS الخاصة بمشغل الهاتف. رغم استحالة قراءة المحتوى بدون مفاتيحك، يحتفظ المشغل ببيانات الإرسال الوصفية المعتادة (الأرقام، الوقت، الأبراج) وفقاً للقوانين المحلية.",
      "legal.t1_title": "1. رخصة الاستخدام وتوفير البرنامج 'كما هو'",
      "legal.t1_desc": "أوربيس برنامج سيادي وخاص يتم توفيره بموجب رخصة استخدام شخصية 'كما هو' دون أي ضمانات صريحة أو ضمنية بما في ذلك الملاءمة لغرض معين أو الخلو من الأخطاء.",
      "legal.t2_title": "2. المسؤولية القانونية الحصرية للمستخدم",
      "legal.t2_desc": "تقر بأنك المسؤول الوحيد عن استخدامك للتطبيق ومراسلاتك، وتتعهد بالامتثال لجميع القوانين واللوائح السارية في بلدك أو نطاقك القضائي.",
      "legal.t3_title": "3. إخلاء مسؤولية المطورين والمساهمين",
      "legal.t3_desc": "يخلي مطورو ومساهمو أوربيس مسؤوليتهم المدنية والجنائية تماماً عن أي أضرار أو خسائر أو أنشطة غير قانونية ناتجة عن إساءة استخدام التطبيق.",
      "legal.t4_title": "4. ميزات الطوارئ (SOS وتحديد الموقع)",
      "legal.t4_desc": "ميزات مشاركة الموقع وتنبيه SOS هي أدوات مساعدة بين النظراء تعتمد على تغطية GSM المحلية ولا تحل محل خدمات الطوارئ الرسمية.",
      "legal.btn_close": "فهمت وأوافق",
      "legal.open_modal_btn": "عرض سياسة الخصوصية والشروط",
      "topics.t_legal_title": "سياسة الخصوصية والشروط وإخلاء المسؤولية",
      "topics.t_legal_content": "أوربيس بنية تحتية سيادية تعمل دون إنترنت بنسبة 100%. لا يتم جمع أو إرسال أي بيانات إلى خوادم سحابية. يتم تشفير الاتصالات طرفاً لطرف (AES-256-GCM / RSA-2048 / Double Ratchet). وبما أنه برنامج سيادي وخاص يُقدَّم \"كما هو\"، يتحمل كل مستخدم وحده المسؤولية القانونية الكاملة عن استخداماته والامتثال للقوانين المحلية.",

      "footer.privacy": "الخصوصية",
      "footer.terms": "الشروط والأحكام",
      "footer.tagline": "بنية اتصالات سيادية ولامركزية بدون إنترنت مطورة بواسطة ShaDevPro."
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

    localStorage.setItem('orbis_lang', lang);
  }

  // Bind Language Switcher Buttons
  document.querySelectorAll('.lang-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const lang = btn.getAttribute('data-lang');
      applyLanguage(lang);
    });
  });

  // Restore saved language preference
  const savedLang = localStorage.getItem('orbis_lang') || 'fr';
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
    localStorage.setItem('orbis_theme', theme);
  }

  const savedTheme = localStorage.getItem('orbis_theme') || 'light';
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
  const ORBIS_CONFIG = {
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
  updateDownloadUrls(ORBIS_CONFIG.apkDownloadUrl);

  const directDownloadBtn = document.getElementById('directDownloadBtn');
  directDownloadBtn?.addEventListener('click', () => {
    console.log('[Orbis] Download APK triggered:', ORBIS_CONFIG.apkDownloadUrl);
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
      console.warn('[Orbis] Clipboard write failed:', e);
    }
  });

  // Open first accordion by default
  if (accordionItems.length > 0) {
    const firstBody = accordionItems[0].querySelector('.accordion-body');
    accordionItems[0].classList.add('active');
    if (firstBody) firstBody.style.maxHeight = firstBody.scrollHeight + 'px';
  }

  // =========================================================================
  // 7. Premium Lightweight Scroll Animations & Reading Progress
  // =========================================================================
  const scrollProgressBar = document.getElementById('scrollProgressBar');
  const backToTopBtn = document.getElementById('backToTopBtn');

  // Throttled scroll listener using requestAnimationFrame for optimal 60fps/120fps performance
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

  // IntersectionObserver for ultra-fluid, zero-overhead Scroll Reveals
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
  // 8. Legal & Privacy Terms Modal Controller (Audit Android APK)
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

  // Open modal triggers anywhere in DOM (footer, topics, etc.)
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
