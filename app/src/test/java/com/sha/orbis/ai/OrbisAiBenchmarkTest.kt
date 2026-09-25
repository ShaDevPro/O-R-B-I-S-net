package com.sha.orbis.ai

import com.sha.orbis.ai.benchmark.OrbisAiBenchmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbisAiBenchmarkTest {

    @Test
    fun benchmark_guard_engine_produces_valid_metrics() {
        val result = OrbisAiBenchmark.runGuardBenchmark(repeatCycles = 2)

        assertNotNull(result)
        assertEquals("ORBIS Guard-LLM", result.engineName)
        assertTrue(result.totalInferences > 0)
        assertTrue(result.avgLatencyMs >= 0.0f)
        assertTrue(result.throughputPerSec > 0)
        assertTrue("Accuracy must be at least 90%", result.accuracyPercent >= 90)
        assertTrue("Score must be high", result.performanceScore >= 80)
        assertTrue(result.testedLanguages.contains("Français"))
        assertTrue(result.testedLanguages.contains("Arabe"))
        assertTrue(result.details.isNotEmpty())
    }

    @Test
    fun benchmark_reply_engine_produces_valid_metrics() {
        val result = OrbisAiBenchmark.runReplyBenchmark(repeatCycles = 2)

        assertNotNull(result)
        assertEquals("ORBIS Reply-LLM", result.engineName)
        assertTrue(result.totalInferences > 0)
        assertTrue(result.avgLatencyMs >= 0.0f)
        assertTrue(result.throughputPerSec > 0)
        assertTrue(result.accuracyPercent >= 90)
        assertTrue(result.performanceScore >= 80)
        assertTrue(result.details.isNotEmpty())
    }

    @Test
    fun benchmark_combined_execution_succeeds() {
        val combined = OrbisAiBenchmark.runCombinedBenchmark()

        assertNotNull(combined.guardResult)
        assertNotNull(combined.replyResult)
        assertTrue(combined.overallScore >= 80)
        assertTrue(combined.hardwareEfficiency.isNotBlank())
    }
}
