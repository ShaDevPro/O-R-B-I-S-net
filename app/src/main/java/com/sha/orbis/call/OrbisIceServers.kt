package com.sha.orbis.call

import org.webrtc.PeerConnection

/**
 * OrbisIceServers — Configuration universelle ICE (STUN & TURN) pour les appels WebRTC OrbisNet.
 *
 * Architecture multi-relais haute disponibilité pour traverser tous les Carrier-Grade NAT (CGNAT)
 * et NAT symétriques des opérateurs mobiles mondiaux (4G/5G/Wi-Fi) :
 *   - STUN : découverte ultra-rapide des IP publiques pour connexions directes P2P
 *   - TURN (Pool mondial de 8+ relais indépendants) : relais chiffré E2EE DTLS-SRTP
 *     en cas de NAT symétrique (Mobilis, Djezzy, Ooredoo, Orange, T-Mobile, Jio, Vodafone, etc.)
 *   - Ports multiples : 80 (HTTP bypass), 443 (HTTPS bypass), 3478 (STUN/TURN standard), 5349 (TURNS)
 *   - Transports : UDP (latence minimale), TCP (pare-feu stricts), TURNS/TLS (inspection DPI)
 *
 * Sécurité & Chiffrement : Les serveurs TURN ne voient que des paquets DTLS-SRTP chiffrés de bout
 * en bout (WebRTC standard RFC 5766 / RFC 8489). Ils ne peuvent en aucun cas déchiffrer l'audio ni la vidéo.
 */
object OrbisIceServers {

    /**
     * Construit la liste complète des serveurs STUN et TURN.
     * WebRTC teste ces serveurs en parallèle pour élire le meilleur candidat
     * (latence la plus basse et stabilité maximale).
     */
    fun build(): List<PeerConnection.IceServer> = buildList {
        // ── 1. SERVEURS STUN (Découverte IP publique haute disponibilité) ──────
        add(stun("stun:stun.l.google.com:19302"))
        add(stun("stun:stun1.l.google.com:19302"))
        add(stun("stun:stun2.l.google.com:19302"))
        add(stun("stun:stun.cloudflare.com:3478"))
        add(stun("stun:stun.freeswitch.org:3478"))
        add(stun("stun:webrtc.free-solutions.org:3478"))
        add(stun("stun:standard.relay.metered.ca:80"))

        // ── 2. RELAIS TURN MONDAUX (Traversée CGNAT & NAT Symétrique 4G/5G) ──

        // Relais 1 : Free-Solutions Europe (Coturn - Port standard + TLS 5349)
        add(turn("turn:webrtc.free-solutions.org:3478?transport=udp", "guest", "guest"))
        add(turn("turn:webrtc.free-solutions.org:3478?transport=tcp", "guest", "guest"))
        add(turn("turns:webrtc.free-solutions.org:5349?transport=tcp", "guest", "guest"))

        // Relais 2 : Metered Global Anycast (Routage dynamique Edge le plus proche)
        add(turn("turn:global.relay.metered.ca:80?transport=udp", "openrelayproject", "openrelayproject"))
        add(turn("turn:global.relay.metered.ca:80?transport=tcp", "openrelayproject", "openrelayproject"))
        add(turn("turn:global.relay.metered.ca:443?transport=tcp", "openrelayproject", "openrelayproject"))
        add(turn("turns:global.relay.metered.ca:443?transport=tcp", "openrelayproject", "openrelayproject"))

        // Relais 3 : Metered Standard (Point d'accès Europe & Afrique du Nord)
        add(turn("turn:standard.relay.metered.ca:80?transport=udp", "openrelayproject", "openrelayproject"))
        add(turn("turn:standard.relay.metered.ca:80?transport=tcp", "openrelayproject", "openrelayproject"))
        add(turn("turn:standard.relay.metered.ca:443?transport=tcp", "openrelayproject", "openrelayproject"))
        add(turn("turns:standard.relay.metered.ca:443?transport=tcp", "openrelayproject", "openrelayproject"))

        // Relais 4 : Metered Région Europe & Méditerranée
        add(turn("turn:eu.relay.metered.ca:80?transport=udp", "openrelayproject", "openrelayproject"))
        add(turn("turn:eu.relay.metered.ca:80?transport=tcp", "openrelayproject", "openrelayproject"))
        add(turn("turns:eu.relay.metered.ca:443?transport=tcp", "openrelayproject", "openrelayproject"))

        // Relais 5 : Metered Région Amérique du Nord
        add(turn("turn:us.relay.metered.ca:80?transport=udp", "openrelayproject", "openrelayproject"))
        add(turn("turn:us.relay.metered.ca:80?transport=tcp", "openrelayproject", "openrelayproject"))
        add(turn("turns:us.relay.metered.ca:443?transport=tcp", "openrelayproject", "openrelayproject"))

        // Relais 6 : Metered Région Asie / Moyen-Orient
        add(turn("turn:asia.relay.metered.ca:80?transport=udp", "openrelayproject", "openrelayproject"))
        add(turn("turn:asia.relay.metered.ca:80?transport=tcp", "openrelayproject", "openrelayproject"))
        add(turn("turns:asia.relay.metered.ca:443?transport=tcp", "openrelayproject", "openrelayproject"))

        // Relais 7 : Matrix Community Public Coturn
        add(turn("turn:turn.matrix.org:3478?transport=udp", "guest", "guest"))
        add(turn("turn:turn.matrix.org:3478?transport=tcp", "guest", "guest"))

        // Relais 8 : OpenRelay Project Multi-Port
        add(turn("turn:openrelay.metered.ca:80", "openrelayproject", "openrelayproject"))
        add(turn("turn:openrelay.metered.ca:443", "openrelayproject", "openrelayproject"))
        add(turn("turn:openrelay.metered.ca:443?transport=tcp", "openrelayproject", "openrelayproject"))
    }

    private fun stun(uri: String) =
        PeerConnection.IceServer.builder(uri).createIceServer()

    private fun turn(uri: String, username: String, credential: String) =
        PeerConnection.IceServer.builder(uri)
            .setUsername(username)
            .setPassword(credential)
            .createIceServer()
}
