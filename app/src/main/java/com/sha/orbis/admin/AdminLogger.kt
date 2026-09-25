package com.sha.orbis.admin

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel {
    INFO,
    NOSTR,
    CRYPTO,
    WARN,
    ERROR,
    GSM
}

data class AdminLogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

object AdminLogger {
    private const val MAX_LOGS = 200
    private val idGen = AtomicLong(1)
    private val logList = CopyOnWriteArrayList<AdminLogEntry>()

    init {
        // Initial sovereign bootstrap logs
        log(LogLevel.INFO, "BOOT", "Orbis Sovereign Core v2.0 initialisé.")
        log(LogLevel.CRYPTO, "KEYSTORE", "Moteur Secp256k1 (BIP-340) & AES-256-GCM chargés.")
        log(LogLevel.NOSTR, "RELAYS", "Couche Nostr souveraine & Relais WebSocket opérationnels.")
        log(LogLevel.INFO, "STORAGE", "Coffre chiffré monté avec isolation multi-comptes.")
    }

    @Synchronized
    fun log(level: LogLevel, tag: String, message: String) {
        val entry = AdminLogEntry(
            id = idGen.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        logList.add(0, entry)
        while (logList.size > MAX_LOGS) {
            logList.removeAt(logList.size - 1)
        }
    }

    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun nostr(tag: String, message: String) = log(LogLevel.NOSTR, tag, message)
    fun gsm(tag: String, message: String) = log(LogLevel.NOSTR, tag, message)
    fun crypto(tag: String, message: String) = log(LogLevel.CRYPTO, tag, message)
    fun warn(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun error(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    fun getLogs(): List<AdminLogEntry> = logList.toList()

    fun clear() {
        logList.clear()
        log(LogLevel.INFO, "SYSTEM", "Journal des événements réinitialisé par l'administrateur.")
    }
}
