package com.sha.orbis.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.Locale

/**
 * High-Assurance Application Integrity and Anti-Tamper Shield for Orbis.
 * Provides multi-layered defense against decompilation, cloning, dynamic hooking (Frida/Xposed),
 * debugging, and unauthorized repackaging.
 */
object AppIntegrityGuard {

    /**
     * Official production certificate SHA-256 hash.
     * When building Release with your production Keystore, update this constant.
     * Example: "4A:3F:88:B1:..."
     */
    var OFFICIAL_RELEASE_SIGNATURE_SHA256: String = "PENDING_RELEASE_KEYSTORE_CREATION"

    /**
     * Comprehensive Security Status Report.
     */
    data class SecurityIntegrityReport(
        val isSignatureValid: Boolean,
        val isDebuggerAttached: Boolean,
        val isFridaDetected: Boolean,
        val isRootDetected: Boolean,
        val isDebugBuild: Boolean,
        val currentSignatureHash: String,
        val isDeviceSecure: Boolean,
        val details: List<String>
    )

    /**
     * Runs full suite of integrity checks and returns consolidated report.
     */
    fun verifyIntegrity(context: Context): SecurityIntegrityReport {
        val isDebug = isDebuggable(context)
        val currentSig = getCurrentSignatureSha256(context)
        val sigValid = if (isDebug || OFFICIAL_RELEASE_SIGNATURE_SHA256 == "PENDING_RELEASE_KEYSTORE_CREATION") {
            true
        } else {
            currentSig.equals(OFFICIAL_RELEASE_SIGNATURE_SHA256.replace(":", "").uppercase(Locale.ROOT), ignoreCase = true) ||
            currentSig.equals(OFFICIAL_RELEASE_SIGNATURE_SHA256.uppercase(Locale.ROOT), ignoreCase = true)
        }

        val debuggerAttached = isDebuggerActive(context)
        val fridaDetected = isFridaOrHookingPresent()
        val rootDetected = isDeviceRooted()

        val details = mutableListOf<String>()
        if (isDebug) details.add("DEBUG_BUILD_ACTIVE")
        if (!sigValid) details.add("SIGNATURE_MISMATCH_TAMPERED")
        if (debuggerAttached) details.add("DEBUGGER_ATTACHED")
        if (fridaDetected) details.add("FRIDA_HOOK_INJECTION_DETECTED")
        if (rootDetected) details.add("ROOT_ENVIRONMENT_DETECTED")

        val isSecure = sigValid && !fridaDetected && (isDebug || (!debuggerAttached && !rootDetected))

        return SecurityIntegrityReport(
            isSignatureValid = sigValid,
            isDebuggerAttached = debuggerAttached,
            isFridaDetected = fridaDetected,
            isRootDetected = rootDetected,
            isDebugBuild = isDebug,
            currentSignatureHash = currentSig,
            isDeviceSecure = isSecure,
            details = details
        )
    }

    /**
     * Checks whether the current application is running in debug mode.
     */
    fun isDebuggable(context: Context): Boolean {
        return try {
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Computes the SHA-256 fingerprint of the active signing certificate.
     */
    @SuppressLint("PackageManagerGetSignatures")
    fun getCurrentSignatureSha256(context: Context): String {
        return try {
            val pm = context.packageManager
            val packageName = context.packageName
            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners.firstOrNull()?.toByteArray()
                    } else {
                        signingInfo.signingCertificateHistory?.firstOrNull()?.toByteArray()
                    }
                } else null
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures?.firstOrNull()?.toByteArray()
            }

            if (certBytes != null) {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(certBytes)
                digest.joinToString(":") { "%02X".format(it) }
            } else {
                "NO_SIGNATURE_FOUND"
            }
        } catch (e: Exception) {
            "SIG_ERROR_"
        }
    }

    /**
     * Detects attached Android / Native debuggers and tracing processes.
     */
    fun isDebuggerActive(context: Context): Boolean {
        // 1. Android runtime debugger API
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            return true
        }

        // 2. Linux TracerPid in /proc/self/status
        try {
            val statusFile = File("/proc/self/status")
            if (statusFile.exists()) {
                BufferedReader(FileReader(statusFile)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line?.startsWith("TracerPid:") == true) {
                            val tracerPid = line?.substringAfter("TracerPid:")?.trim()?.toIntOrNull() ?: 0
                            if (tracerPid > 0) return true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Detects dynamic memory hooking frameworks (Frida, Xposed, Substrate).
     */
    fun isFridaOrHookingPresent(): Boolean {
        // 1. Inspect loaded memory maps
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists()) {
                BufferedReader(FileReader(mapsFile)).use { reader ->
                    var line: String?
                    val suspiciousKeywords = listOf(
                        "frida", "gadget", "xposed", "substrate", "edxposed",
                        "lsposed", "sandhook", "riru", "zygisk"
                    )
                    while (reader.readLine().also { line = it } != null) {
                        val lower = line?.lowercase(Locale.ROOT) ?: ""
                        for (kw in suspiciousKeywords) {
                            if (lower.contains(kw)) return true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Check Frida default server port (27042)
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", 27042), 50)
                return true // Socket connected, Frida server is running
            }
        } catch (_: Exception) {}

        // 3. Inspect named threads
        try {
            val taskDir = File("/proc/self/task")
            if (taskDir.exists() && taskDir.isDirectory) {
                val tasks = taskDir.listFiles()
                if (tasks != null) {
                    for (t in tasks) {
                        val comm = File(t, "comm")
                        if (comm.exists()) {
                            val name = comm.readText().lowercase(Locale.ROOT)
                            if (name.contains("gmain") || name.contains("gdbus") || name.contains("frida")) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Multi-heuristic root and custom-ROM compromise detection.
     */
    fun isDeviceRooted(): Boolean {
        // 1. Check common su binary locations
        val suPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in suPaths) {
            if (File(path).exists()) return true
        }

        // 2. Check Build Tags for test-keys
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        // 3. Check execution capability
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readLine() != null
            }
        } catch (_: Exception) {
            false
        }
    }
}
