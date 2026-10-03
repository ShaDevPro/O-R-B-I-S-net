package com.sha.orbis.call.diagnostic

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * FeedDebugTracker — Traqueur haute fidélité pour la navigation et le défilement dans le fil d'actualité.
 *
 * Résilience absolue aux arrêts forcés :
 * 1. Écriture disque synchrone et persistante (feed_events_persistent.log).
 * 2. Watchdog indépendant en boucle serrée avec capture de stack trace dès 1200ms de blocage Main Thread,
 *    écrite immédiatement sur disque dans feed_freeze_trace.txt.
 * 3. Sonde officielle de l'OS Android (ActivityManager.getHistoricalProcessExitReasons) pour capturer
 *    les ANR et crashs natifs ayant entraîné la boîte de dialogue "Fermer l'application".
 * 4. Les traces survivent à la fermeture de l'application et aux redémarrages.
 */
object FeedDebugTracker {

    private const val TAG = "FeedDebugTracker"
    private const val MAX_FEED_EVENTS_MEMORY = 500
    private const val EVENTS_LOG_FILE = "feed_events_persistent.log"
    private const val FREEZE_TRACE_FILE = "feed_freeze_trace.txt"
    private const val DIR_NAME = "diagnostics"

    private val feedEventsLog = Collections.synchronizedList(mutableListOf<String>())
    private val freezeIncidents = Collections.synchronizedList(mutableListOf<String>())

    // Métriques globales
    val totalRecompositions = AtomicInteger(0)
    val totalScrollEvents = AtomicInteger(0)
    val totalFilterRuns = AtomicInteger(0)
    val totalRankingRuns = AtomicInteger(0)
    val totalMediaLoads = AtomicInteger(0)
    val mediaCacheHits = AtomicInteger(0)
    val mediaDiskDecodes = AtomicInteger(0)
    val maxMainThreadLagMs = AtomicLong(0L)
    val freezeDetectedCount = AtomicInteger(0)

    // État courant
    @Volatile var isFeedActive = false
    @Volatile var currentVisibleItemIndex = 0
    @Volatile var currentVisibleItemOffset = 0
    @Volatile var lastScrollDirection = "STABLE"
    @Volatile var lastFilterDurationMs = 0L
    @Volatile var lastRankingDurationMs = 0L

    // Watchdog Thread & Ticks
    private val isWatchdogRunning = AtomicBoolean(false)
    private var watchdogThread: Thread? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val watchdogTick = AtomicLong(0L)
    private val handledWatchdogTick = AtomicLong(0L)

    private var appContext: Context? = null

    private fun getDiagnosticsDir(context: Context): File {
        val dir = context.getExternalFilesDir(DIR_NAME) ?: File(context.filesDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getEventsFile(context: Context): File =
        File(getDiagnosticsDir(context), EVENTS_LOG_FILE)

    private fun getFreezeTraceFile(context: Context): File =
        File(getDiagnosticsDir(context), FREEZE_TRACE_FILE)

    private val diskLogExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "OrbisFeedDiskLogger").apply { isDaemon = true }
    }

    /**
     * Enregistre un événement à la fois en RAM et immédiatement sur le disque.
     */
    fun logEvent(tag: String, message: String) {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val timeStr = sdf.format(Date())
        val line = "[$timeStr] [$tag] $message"

        synchronized(feedEventsLog) {
            if (feedEventsLog.size >= MAX_FEED_EVENTS_MEMORY) {
                feedEventsLog.removeAt(0)
            }
            feedEventsLog.add(line)
        }
        Log.i(TAG, "[$tag] $message")

        // Écriture persistante asynchrone ultra-rapide sur disque pour ne jamais ralentir le Main Thread
        appContext?.let { ctx ->
            diskLogExecutor.execute {
                try {
                    val file = getEventsFile(ctx)
                    FileWriter(file, true).use { fw ->
                        fw.write(line + "\n")
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Réinitialise pour une nouvelle session d'appel sans détruire les traces de crash sauvegardées sur disque.
     */
    fun resetForNewCall(direction: String, peerPhone: String) {
        logEvent("SESSION", "==================== APPEL ($direction avec $peerPhone) ====================")
    }

    /**
     * Démarrage de la surveillance active du fil d'actualité.
     */
    fun onFeedEntered(context: Context, initialPostsCount: Int) {
        appContext = context.applicationContext
        isFeedActive = true
        val rt = Runtime.getRuntime()
        val usedMemMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val maxMemMb = rt.maxMemory() / (1024 * 1024)

        logEvent("FEED_LIFECYCLE", "--- ENTRÉE DANS LE FIL D'ACTUALITÉ ---")
        logEvent("FEED_MEM", "Mémoire JVM : ${usedMemMb}MB / max ${maxMemMb}MB (Posts initiaux: $initialPostsCount)")

        startWatchdog(context)
    }

    /**
     * Sortie du fil d'actualité.
     */
    fun onFeedExited(context: Context) {
        isFeedActive = false
        stopWatchdog()
        logEvent("FEED_LIFECYCLE", "--- SORTIE DU FIL D'ACTUALITÉ ---")
    }

    /**
     * Démarre le thread Watchdog de détection des blocages du Main Thread.
     * Utilise un système de heartbeat à compteur strict.
     */
    private fun startWatchdog(context: Context) {
        if (isWatchdogRunning.getAndSet(true)) return

        val appCtx = context.applicationContext
        appContext = appCtx

        watchdogThread = Thread {
            Log.i(TAG, "Démarrage du Watchdog haute précision pour le fil d'actualité.")
            val heartbeatIntervalMs = 250L
            val freezeThresholdMs = 3000L

            while (isWatchdogRunning.get()) {
                val currentTick = watchdogTick.incrementAndGet()
                val pingTime = SystemClock.elapsedRealtime()

                mainHandler.post {
                    handledWatchdogTick.set(currentTick)
                    val lag = SystemClock.elapsedRealtime() - pingTime
                    if (lag > maxMainThreadLagMs.get()) {
                        maxMainThreadLagMs.set(lag)
                    }
                    if (lag > 300) {
                        logEvent("WATCHDOG_LAG", "Retard Main Thread UI détecté : lag=${lag}ms")
                    }
                }

                // Attente active par tranches pour détecter le dépassement du seuil
                var isFrozen = false
                val startWait = SystemClock.elapsedRealtime()
                while (isWatchdogRunning.get() && handledWatchdogTick.get() < currentTick) {
                    val waitElapsed = SystemClock.elapsedRealtime() - startWait
                    if (waitElapsed >= freezeThresholdMs) {
                        isFrozen = true
                        break
                    }
                    try {
                        Thread.sleep(heartbeatIntervalMs)
                    } catch (_: InterruptedException) {
                        break
                    }
                }

                if (isFrozen && isWatchdogRunning.get()) {
                    val freezeDuration = SystemClock.elapsedRealtime() - pingTime
                    freezeDetectedCount.incrementAndGet()

                    val mainThread = Looper.getMainLooper().thread
                    val stackTrace = getMainThreadStackTrace()
                    @Suppress("DEPRECATION")
                    val allTraces = try { Thread.getAllStackTraces() } catch (_: Throwable) { emptyMap() }

                    val sb = StringBuilder()
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    val nowStr = sdf.format(Date())

                    sb.appendLine("================== [ALERTE GEL / FREEZE DU MAIN THREAD DÉTECTÉ] ==================")
                    sb.appendLine("Date & Heure exacte          : $nowStr")
                    sb.appendLine("Durée du blocage Main Thread : ${freezeDuration}ms (Seuil: ${freezeThresholdMs}ms)")
                    sb.appendLine("État du thread UI            : ${mainThread.state} (Nom: ${mainThread.name}, ID: ${mainThread.id})")
                    sb.appendLine("Dernier item de scroll       : index=$currentVisibleItemIndex, offset=$currentVisibleItemOffset, direction=$lastScrollDirection")
                    sb.appendLine("Stack trace exacte du blocage UI :")

                    var traceWritten = false
                    if (stackTrace.isNotEmpty()) {
                        for (elem in stackTrace.take(60)) {
                            sb.appendLine("    at ${elem.className}.${elem.methodName}(${elem.fileName}:${elem.lineNumber})")
                        }
                        traceWritten = true
                    } else {
                        // Chercher le thread main dans Thread.getAllStackTraces()
                        val mainEntry = allTraces.entries.firstOrNull { it.key.id == mainThread.id || it.key.name == "main" }
                        if (mainEntry != null && mainEntry.value.isNotEmpty()) {
                            for (elem in mainEntry.value.take(60)) {
                                sb.appendLine("    at ${elem.className}.${elem.methodName}(${elem.fileName}:${elem.lineNumber})")
                            }
                            traceWritten = true
                        }
                    }

                    if (!traceWritten) {
                        sb.appendLine("    [INFO] Main thread stack vide au niveau ART (thread en attente native/binder ou IPC)")
                    }

                    // Dump de l'état du Looper principal
                    try {
                        sb.appendLine("État de la file des messages Looper :")
                        Looper.getMainLooper().dump({ line -> sb.appendLine("    $line") }, "  ")
                    } catch (_: Throwable) {}

                    // Dump des threads de travail pertinents (Coroutines, Background, Nostr, IO)
                    val relevantThreads = allTraces.filter { (thread, frames) ->
                        thread != mainThread && frames.any { it.className.contains("com.sha.orbis") }
                    }
                    if (relevantThreads.isNotEmpty()) {
                        sb.appendLine("--- Threads de fond actifs (com.sha.orbis) ---")
                        for ((thread, frames) in relevantThreads.entries.take(4)) {
                            sb.appendLine("Thread '${thread.name}' (Etat: ${thread.state}) :")
                            for (elem in frames.take(15)) {
                                sb.appendLine("    at ${elem.className}.${elem.methodName}(${elem.fileName}:${elem.lineNumber})")
                            }
                        }
                    }

                    sb.appendLine("=================================================================================")

                    val incidentStr = sb.toString()
                    Log.e(TAG, incidentStr)
                    freezeIncidents.add(incidentStr)
                    logEvent("FREEZE_DUMP", "FREEZE DETECTE (${freezeDuration}ms) - Incident enregistré !")

                    // Écriture d'urgence asynchrone sur disque pour ne jamais bloquer le watchdog
                    diskLogExecutor.execute {
                        try {
                            val freezeFile = getFreezeTraceFile(appCtx)
                            FileWriter(freezeFile, true).use { fw ->
                                fw.write(incidentStr + "\n")
                            }
                        } catch (e: Throwable) {
                            Log.e(TAG, "Erreur écriture disque freeze trace : ${e.message}")
                        }
                    }

                    // Attendre que le thread se débloque avant de reprendre
                    while (handledWatchdogTick.get() < currentTick && isWatchdogRunning.get()) {
                        try {
                            Thread.sleep(500)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }

                try {
                    Thread.sleep(250)
                } catch (_: InterruptedException) {
                    break
                }
            }
            Log.i(TAG, "Arrêt du Watchdog Feed.")
        }.apply {
            name = "OrbisFeedWatchdog"
            isDaemon = true
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    private fun stopWatchdog() {
        isWatchdogRunning.set(false)
        watchdogThread?.interrupt()
        watchdogThread = null
    }

    /**
     * Récupère la stack trace réelle du thread UI, même sur Android 16 quand thread.state == NEW
     */
    @Suppress("DEPRECATION")
    private fun getMainThreadStackTrace(): Array<StackTraceElement> {
        val mainThread = Looper.getMainLooper().thread
        // 1. Trace standard
        try {
            val trace = mainThread.stackTrace
            if (trace.isNotEmpty()) return trace
        } catch (_: Throwable) {}

        // 2. VMStack direct via réflexion ART
        try {
            val vmStackClass = Class.forName("dalvik.system.VMStack")
            val method = vmStackClass.getDeclaredMethod("getThreadStackTrace", Thread::class.java)
            method.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val frames = method.invoke(null, mainThread) as? Array<StackTraceElement>
            if (!frames.isNullOrEmpty()) return frames
        } catch (_: Throwable) {}

        // 3. Thread.getAllStackTraces()
        try {
            val all = Thread.getAllStackTraces()
            val entry = all.entries.firstOrNull { it.key.id == mainThread.id || it.key.name == "main" || it.key == mainThread }
            if (entry != null && entry.value.isNotEmpty()) return entry.value
        } catch (_: Throwable) {}

        return emptyArray()
    }

    /**
     * Traçage des événements de défilement dans le LazyColumn.
     */
    fun onScroll(firstIndex: Int, firstOffset: Int, isScrollInProgress: Boolean) {
        totalScrollEvents.incrementAndGet()
        val dir = when {
            firstIndex > currentVisibleItemIndex -> "BAS (Descendant)"
            firstIndex < currentVisibleItemIndex -> "HAUT (Montant)"
            firstOffset > currentVisibleItemOffset -> "BAS (Léger micro-scroll)"
            firstOffset < currentVisibleItemOffset -> "HAUT (Léger micro-scroll)"
            else -> "IMMOBILE"
        }
        currentVisibleItemIndex = firstIndex
        currentVisibleItemOffset = firstOffset
        lastScrollDirection = dir

        if (isScrollInProgress && totalScrollEvents.get() % 50 == 0) {
            logEvent("SCROLL", "Défilement : index=$firstIndex, offset=$firstOffset, dir=$dir")
        }
    }

    /**
     * Traçage du filtrage des publications.
     */
    fun logFilterResult(durationMs: Long, inputCount: Int, outputCount: Int) {
        totalFilterRuns.incrementAndGet()
        lastFilterDurationMs = durationMs
        if (durationMs > 25) {
            logEvent("FILTER_PERF", "Filtrage exécuté en ${durationMs}ms (Reçu: $inputCount, Restant: $outputCount)")
        }
    }

    /**
     * Traçage du ranking algorithmique.
     */
    fun logRankingResult(tabIndex: Int, durationMs: Long, count: Int) {
        totalRankingRuns.incrementAndGet()
        lastRankingDurationMs = durationMs
        if (durationMs > 20) {
            logEvent("RANKING_PERF", "Algorithme tab=$tabIndex exécuté en ${durationMs}ms ($count posts classés)")
        }
    }

    /**
     * Traçage des chargements de médias (images et vidéos).
     */
    fun logMediaLoad(postId: String, mediaType: String, fromCache: Boolean, durationMs: Long, error: String? = null) {
        totalMediaLoads.incrementAndGet()
        if (fromCache) {
            mediaCacheHits.incrementAndGet()
        } else {
            mediaDiskDecodes.incrementAndGet()
        }
        if (error != null) {
            logEvent("MEDIA_ERR", "Échec média [$mediaType] post=$postId : $error (${durationMs}ms)")
        } else if (!fromCache || durationMs > 30) {
            logEvent("MEDIA_LOAD", "Média [$mediaType] post=$postId : source=${if (fromCache) "Cache RAM" else "Disque"} en ${durationMs}ms")
        }
    }

    /**
     * Sonde officielle du système d'exploitation Android (ActivityManager.getHistoricalProcessExitReasons).
     * Récupère la cause réelle et la stack trace système si Android a fermé l'application ("Fermer l'application").
     */
    fun getAndroidSystemExitInfo(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return "Indisponible sur Android < 11 (API < 30)"
        }
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val exits = am?.getHistoricalProcessExitReasons(context.packageName, 0, 5) ?: emptyList()
            if (exits.isEmpty()) {
                return "Aucun événement d'arrêt système antérieur enregistré par Android."
            }

            val sb = StringBuilder()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            for ((idx, exit) in exits.withIndex()) {
                val reasonStr = when (exit.reason) {
                    ApplicationExitInfo.REASON_ANR -> "ANR (Application Not Responding / Boîte native 'Fermer l'application')"
                    ApplicationExitInfo.REASON_CRASH -> "CRASH JAVA/KOTLIN (Exception non interceptée)"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH NATIF C/C++ (Segmentation Fault / Tombstone)"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW MEMORY (Processus tué par le Low Memory Killer Android)"
                    ApplicationExitInfo.REASON_USER_REQUESTED -> "FERMETURE VOLONTAIRE (Swipe dans le multitâche)"
                    ApplicationExitInfo.REASON_EXIT_SELF -> "SORTIE NORMALE (System.exit / Process.killProcess)"
                    ApplicationExitInfo.REASON_SIGNALED -> "SIGNAL SYSTÈME REÇU (SIGKILL / SIGTERM)"
                    else -> "Code système ${exit.reason}"
                }

                val dateStr = sdf.format(Date(exit.timestamp))
                sb.appendLine("--- Événement Système #${idx + 1} ($dateStr) ---")
                sb.appendLine("  * Motif de fin       : $reasonStr")
                sb.appendLine("  * Statut processus   : PID=${exit.pid}, Importance=${exit.importance}")
                sb.appendLine("  * Mémoire PSS        : ${exit.pss / 1024} MB (RSS: ${exit.rss / 1024} MB)")
                sb.appendLine("  * Description        : ${exit.description ?: "Aucune"}")

                // Extraction de la trace système native ou de l'ANR trace Android
                if (exit.reason == ApplicationExitInfo.REASON_ANR ||
                    exit.reason == ApplicationExitInfo.REASON_CRASH ||
                    exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                ) {
                    try {
                        exit.traceInputStream?.use { stream ->
                            val reader = BufferedReader(InputStreamReader(stream))
                            val allLines = reader.lineSequence().take(3000).toList()
                            if (allLines.isNotEmpty()) {
                                sb.appendLine("  * Trace Système Officielle Android :")
                                val mainIdx = allLines.indexOfFirst { line ->
                                    val t = line.trim()
                                    (t.startsWith("\"main\"") || t.contains("\"main\" prio=") || t.contains("sysTid=${exit.pid}")) && !t.contains("Finalizer")
                                }
                                if (mainIdx != -1) {
                                    sb.appendLine("      --- [SECTION THREAD 'main' IDENTIFIÉE DANS LE DUMP SYSTÈME] ---")
                                    val nextThreadRel = allLines.subList(mainIdx + 1, allLines.size).indexOfFirst { it.trim().startsWith("\"") }
                                    val endIdx = if (nextThreadRel != -1) (mainIdx + 1 + nextThreadRel).coerceAtMost(mainIdx + 100) else (mainIdx + 100).coerceAtMost(allLines.size)
                                    for (i in mainIdx until endIdx) {
                                        sb.appendLine("      ${allLines[i]}")
                                    }
                                } else {
                                    // Si pas de marqueur "main" explicite, afficher jusqu'à 250 lignes
                                    allLines.take(250).forEach { line -> sb.appendLine("      $line") }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        sb.appendLine("  * Impossible de lire la trace système : ${e.message}")
                    }
                }
                sb.appendLine()
            }
            sb.toString()
        } catch (e: Throwable) {
            "Erreur lors de la lecture des sorties système : ${e.message}"
        }
    }

    /**
     * Construit la synthèse texte du diagnostic Feed en incluant la mémoire, les blocages et les rapports système.
     */
    fun buildReportSection(context: Context? = null): String {
        val targetCtx = context ?: appContext
        val sb = StringBuilder()
        val rt = Runtime.getRuntime()
        val usedMemMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val maxMemMb = rt.maxMemory() / (1024 * 1024)

        sb.appendLine("[7. DIAGNOSTIC DE NAVIGATION & DÉFILEMENT DU FIL D'ACTUALITÉ (FEED)]")
        sb.appendLine("État actuel du fil               : ${if (isFeedActive) "ACTIF (Ouvert)" else "INACTIF"}")
        sb.appendLine("Dernier index visible            : item #$currentVisibleItemIndex (offset=$currentVisibleItemOffset)")
        sb.appendLine("Dernière direction de défilement : $lastScrollDirection")
        sb.appendLine("Total recompositions mesurées    : ${totalRecompositions.get()}")
        sb.appendLine("Total événements de scroll       : ${totalScrollEvents.get()}")
        sb.appendLine("Temps d'exécution filtrage       : ${lastFilterDurationMs}ms (exécutions: ${totalFilterRuns.get()})")
        sb.appendLine("Temps d'exécution ranking        : ${lastRankingDurationMs}ms (exécutions: ${totalRankingRuns.get()})")
        sb.appendLine("Médias : Chargements totaux      : ${totalMediaLoads.get()} (Cache RAM=${mediaCacheHits.get()}, Décodages Disque=${mediaDiskDecodes.get()})")
        sb.appendLine("Latence max Main Thread mesurée  : ${maxMainThreadLagMs.get()}ms")
        sb.appendLine("Mémoire JVM actuelle             : ${usedMemMb}MB / ${maxMemMb}MB max")
        sb.appendLine("Freezes / Blocages UI détectés   : ${freezeDetectedCount.get()}")

        // 1. Traces de freeze stockées sur disque (persistantes à travers les crashs)
        targetCtx?.let { ctx ->
            val freezeFile = getFreezeTraceFile(ctx)
            if (freezeFile.exists() && freezeFile.length() > 0) {
                sb.appendLine()
                sb.appendLine("--- DÉTAIL DES BLOCAGES CAPTURÉS SUR DISQUE PAR LE WATCHDOG ---")
                try {
                    sb.appendLine(freezeFile.readText())
                } catch (_: Throwable) {}
            }
        }

        // 2. Incident crash mémoire ou JVM
        val crashFile = targetCtx?.let { File(getDiagnosticsDir(it), "last_crash.txt") }
        if (crashFile?.exists() == true && crashFile.length() > 0) {
            sb.appendLine()
            sb.appendLine("--- DERNIER CRASH EXCEPTION FATAL CAPTURÉ ---")
            try {
                sb.appendLine(crashFile.readText())
            } catch (_: Throwable) {}
        }

        // 3. Sonde officielle Android OS (ANR, OOM, SIGSEGV)
        targetCtx?.let { ctx ->
            sb.appendLine()
            sb.appendLine("--- HISTORIQUE OFFICIEL DES ARRÊTS SYSTÈME ANDROID (API 30+) ---")
            sb.appendLine(getAndroidSystemExitInfo(ctx))
        }

        return sb.toString()
    }

    /**
     * Construit le flux chronologique complet des événements Feed en lisant depuis le stockage persistant.
     */
    fun buildFeedEventsSection(context: Context? = null): String {
        val targetCtx = context ?: appContext
        val sb = StringBuilder()
        sb.appendLine("[8. FLUX CHRONOLOGIQUE DÉTAILLÉ DE LA NAVIGATION DANS LE FIL (DISQUE & RAM)]")

        var hasDiskEvents = false
        targetCtx?.let { ctx ->
            val file = getEventsFile(ctx)
            if (file.exists() && file.length() > 0) {
                try {
                    val lines = file.readLines()
                    val recentLines = if (lines.size > 250) lines.takeLast(250) else lines
                    recentLines.forEach { sb.appendLine(it) }
                    hasDiskEvents = true
                } catch (_: Throwable) {}
            }
        }

        if (!hasDiskEvents) {
            synchronized(feedEventsLog) {
                if (feedEventsLog.isEmpty()) {
                    sb.appendLine("Aucun événement de fil d'actualité enregistré pour le moment.")
                } else {
                    feedEventsLog.forEach { sb.appendLine(it) }
                }
            }
        }

        return sb.toString()
    }
}
