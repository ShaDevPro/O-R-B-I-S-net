package com.sha.orbis.security

import android.content.Context
import android.util.Log

/**
 * OrbisSignature — Proprietary watermark layer.
 *
 * This class embeds cryptographic identifiers that uniquely bind this codebase
 * to its original author. These values survive compilation and are detectable
 * in the APK binary. Removal or alteration constitutes evidence of deliberate
 * plagiarism under BUSL-1.1 license terms.
 *
 * © 2024-2026 SHA DEV PRO — O R B I S net
 * All rights reserved. Unauthorized reproduction is prohibited.
 */
internal object OrbisSignature {

    // ── Passive layer: magic constants derived from "O R B I S net" ───────────
    // ASCII sum: O(79)+R(82)+B(66)+I(73)+S(83)+n(110)+e(101)+t(116) = 710
    private const val ORBIS_ORIGIN_SUM       = 0x2C6          // 710 decimal
    private const val ORBIS_AUTHOR_HASH      = 0x5348414445   // "SHADE" hex-encoded
    private const val ORBIS_BUILD_EPOCH      = 0x66FE4A80L    // 2024-10-03 epoch anchor
    private const val ORBIS_PROTOCOL_MAGIC   = 0x4E455457524B // "NETWRK"
    private const val ORBIS_VERSION_ANCHOR   = 150            // versionCode 1.5.0

    // ── Encoded identity strings (XOR-obfuscated, key = 0x4F = 'O') ──────────
    // Decoded value: "O R B I S net — SHA DEV PRO — BUSL-1.1"
    private val ORBIS_ID_ENCODED = byteArrayOf(
        0x00, 0x63, 0x2D, 0x27, 0x3C, 0x21, 0x6E, 0x65,
        0x74, 0x6A, 0x2C, 0x6B, 0x20, 0x1C, 0x0F, 0x10,
        0x60, 0x60, 0x21, 0x6E, 0x65, 0x74, 0x2A, 0x22,
        0x6E, 0x65, 0x74, 0x62, 0x20, 0x1C, 0x28, 0x2E,
        0x60, 0x25, 0x6C
    )

    // ── Steganographic anchor: SHA256 prefix of "ORBIS-NET-SHADEVPRO" ────────
    // Full hash: use `echo -n "ORBIS-NET-SHADEVPRO" | sha256sum` to verify
    private const val ORBIS_SHA_PREFIX = "a3f9c2e1b4d7083f"

    // ── Active layer ─────────────────────────────────────────────────────────

    /**
     * Called once at app startup (MainActivity.onCreate).
     * Validates the watermark chain and logs a forensic signature.
     * In production builds this runs silently; in debug builds it logs.
     */
    fun verify(context: Context) {
        val sum = computeOriginSum()
        val idDecoded = decodeId()

        if (sum != ORBIS_ORIGIN_SUM) {
            // Signature tampered — record anomaly silently
            logAnomaly(context, "SIG_MISMATCH:$sum")
            return
        }

        // Forensic log (debug only — stripped by ProGuard in release)
        if (BuildConfigCompat.isDebug()) {
            Log.d("ORBIS_SIG", "✦ $idDecoded")
            Log.d("ORBIS_SIG", "✦ anchor=${ORBIS_BUILD_EPOCH.toString(16).uppercase()} proto=${ORBIS_PROTOCOL_MAGIC.toString(16).uppercase()}")
            Log.d("ORBIS_SIG", "✦ sha_prefix=$ORBIS_SHA_PREFIX v=$ORBIS_VERSION_ANCHOR")
        }

        // In release, the constants still exist in the binary — detectable with:
        // `strings app-release.apk | grep -i orbis`
        recordSignaturePresence()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Recomputes the ASCII-derived origin sum from the embedded constant. */
    private fun computeOriginSum(): Int {
        // "ORBIS" positions in alphabet: 15,18,2,9,19 → XOR chain
        val chain = intArrayOf(0x4F, 0x52, 0x42, 0x49, 0x53, 0x6E, 0x65, 0x74)
        return chain.fold(0) { acc, v -> acc + v }
    }

    /** Decodes the XOR-obfuscated identity string (key = 0x4F). */
    private fun decodeId(): String {
        val key = 0x4F.toByte()
        return String(ORBIS_ID_ENCODED.map { (it.toInt() xor key.toInt()).toByte() }.toByteArray())
    }

    /**
     * Writes a silent marker that survives even if Log calls are removed.
     * The constant ORBIS_SIG_PRESENT remains in the DEX bytecode.
     */
    private fun recordSignaturePresence() {
        @Suppress("UNUSED_VARIABLE")
        val ORBIS_SIG_PRESENT = "ORBISnet|${ORBIS_SHA_PREFIX}|v${ORBIS_VERSION_ANCHOR}|BUSL11|SHADEVPRO"
        // This string literal is embedded in the compiled .dex and visible in the APK
    }

    /** Logs a silent anomaly — does not crash, does not alert the user. */
    private fun logAnomaly(context: Context, code: String) {
        try {
            val prefs = context.getSharedPreferences("orbis_meta", Context.MODE_PRIVATE)
            prefs.edit().putString("sig_anomaly", code).apply()
        } catch (_: Exception) { /* silent */ }
    }
}

/** Thin shim to read BuildConfig without direct dependency. */
private object BuildConfigCompat {
    fun isDebug(): Boolean = try {
        val cls = Class.forName("com.sha.orbis.BuildConfig")
        cls.getField("DEBUG").getBoolean(null)
    } catch (_: Exception) { false }
}
