package com.sha.orbis

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun gpsPayload_formatAndParse_isCorrect() {
        val lat = 36.752500
        val lon = 3.041970
        val payload = com.sha.orbis.media.LocationGpsHelper.formatGpsPayload(lat, lon)
        assertEquals("[GPS:36.752500,3.041970]", payload)

        val parsed = com.sha.orbis.media.LocationGpsHelper.parseGpsPayload(payload)
        assertNotNull(parsed)
        assertEquals(lat, parsed!!.first, 0.000001)
        assertEquals(lon, parsed.second, 0.000001)
    }

    @Test
    fun gpsPayload_parseLegacyFrenchComma_isCorrect() {
        // Test retroactive parsing of old bugged format: [GPS:36,75250,3,04197]
        val legacy = "Urgence SOS [GPS:36,75250,3,04197] Besoin d'aide"
        val parsed = com.sha.orbis.media.LocationGpsHelper.parseGpsPayload(legacy)
        assertNotNull(parsed)
        assertEquals(36.75250, parsed!!.first, 0.000001)
        assertEquals(3.04197, parsed.second, 0.000001)
    }

    @Test
    fun linkPlatform_detection_isCorrect() {
        assertEquals(com.sha.orbis.media.LinkPlatform.TIKTOK, com.sha.orbis.media.LinkPlatform.fromUrl("https://www.tiktok.com/@creator/video/71234567890"))
        assertEquals(com.sha.orbis.media.LinkPlatform.TIKTOK, com.sha.orbis.media.LinkPlatform.fromUrl("https://vm.tiktok.com/ZMxxxxxx/"))
        assertEquals(com.sha.orbis.media.LinkPlatform.INSTAGRAM, com.sha.orbis.media.LinkPlatform.fromUrl("https://www.instagram.com/reel/C34xyz123/?igsh=abc"))
        assertEquals(com.sha.orbis.media.LinkPlatform.INSTAGRAM, com.sha.orbis.media.LinkPlatform.fromUrl("https://instagr.am/p/C34xyz/"))
        assertEquals(com.sha.orbis.media.LinkPlatform.FACEBOOK, com.sha.orbis.media.LinkPlatform.fromUrl("https://www.facebook.com/share/p/12345/"))
        assertEquals(com.sha.orbis.media.LinkPlatform.FACEBOOK, com.sha.orbis.media.LinkPlatform.fromUrl("https://fb.watch/123456/"))
        assertEquals(com.sha.orbis.media.LinkPlatform.YOUTUBE, com.sha.orbis.media.LinkPlatform.fromUrl("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(com.sha.orbis.media.LinkPlatform.GENERIC, com.sha.orbis.media.LinkPlatform.fromUrl("https://github.com/ShaDevPro/O-R-B-I-S-net"))
    }

    @Test
    fun linkPreviewHelper_extractFirstUrl_isCorrect() {
        val textWithNote = "Regarde ce super reel sur Instagram : https://www.instagram.com/reel/C34xyz/ ! Trop drôle"
        val extracted = com.sha.orbis.media.LinkPreviewHelper.extractFirstUrl(textWithNote)
        assertEquals("https://www.instagram.com/reel/C34xyz/", extracted)

        val rawTiktok = "https://www.tiktok.com/@user/video/123456"
        assertEquals("https://www.tiktok.com/@user/video/123456", com.sha.orbis.media.LinkPreviewHelper.extractFirstUrl(rawTiktok))

        val noUrl = "Un message sans aucun lien web"
        assertNull(com.sha.orbis.media.LinkPreviewHelper.extractFirstUrl(noUrl))
    }
}