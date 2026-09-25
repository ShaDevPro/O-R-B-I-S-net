package com.sha.orbis.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAttachmentHelperTest {

    @Test
    fun imagePayload_buildAndParse_roundTrip() {
        val testBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        val caption = "Vacances à la mer 2026"
        val payload = MediaAttachmentHelper.buildImagePayload("img_12345", testBase64, caption)

        assertTrue(MediaAttachmentHelper.isImagePayload(payload))
        assertFalse(MediaAttachmentHelper.isDocPayload(payload))

        val parsed = MediaAttachmentHelper.parseImagePayload(payload)
        assertNotNull(parsed)
        assertEquals("img_12345", parsed!!.id)
        assertEquals(testBase64, parsed.base64Data)
        assertEquals(caption, parsed.caption)
    }

    @Test
    fun imagePayload_withoutCaption_roundTrip() {
        val testBase64 = "AQIDBAUGBwgJCgsMDQ4PEA=="
        val payload = MediaAttachmentHelper.buildImagePayload("img_999", testBase64)

        assertTrue(MediaAttachmentHelper.isImagePayload(payload))

        val parsed = MediaAttachmentHelper.parseImagePayload(payload)
        assertNotNull(parsed)
        assertEquals("img_999", parsed!!.id)
        assertEquals(testBase64, parsed.base64Data)
        assertEquals("", parsed.caption)
    }

    @Test
    fun docPayload_buildAndParse_roundTrip() {
        val testDocData = "SGVsbG8gT3JiaXNOZXQgU292ZXJlaWduIERvY3VtZW50"
        val fileName = "contrat_confidentiel.pdf"
        val fileSizeFormatted = "245 KB"
        val payload = MediaAttachmentHelper.buildDocPayload("doc_555", fileName, fileSizeFormatted, testDocData)

        assertTrue(MediaAttachmentHelper.isDocPayload(payload))
        assertFalse(MediaAttachmentHelper.isImagePayload(payload))

        val parsed = MediaAttachmentHelper.parseDocPayload(payload)
        assertNotNull(parsed)
        assertEquals("doc_555", parsed!!.id)
        assertEquals(fileName, parsed.fileName)
        assertEquals(fileSizeFormatted, parsed.fileSizeFormatted)
        assertEquals(testDocData, parsed.base64Data)
    }

    @Test
    fun isPayload_invalidStrings_returnFalse() {
        assertFalse(MediaAttachmentHelper.isImagePayload("Hello world"))
        assertFalse(MediaAttachmentHelper.isDocPayload("Hello world"))
        assertFalse(MediaAttachmentHelper.isImagePayload("[IMAGE:incomplete"))
        assertFalse(MediaAttachmentHelper.isDocPayload("[DOC:incomplete"))
        assertNull(MediaAttachmentHelper.parseImagePayload("Not a payload"))
        assertNull(MediaAttachmentHelper.parseDocPayload("Not a payload"))
    }

    @Test
    fun formatFileSize_scalesCorrectly() {
        assertEquals("512 B", MediaAttachmentHelper.formatFileSize(512L))
        assertEquals("10 KB", MediaAttachmentHelper.formatFileSize(10 * 1024L))
        assertTrue(MediaAttachmentHelper.formatFileSize(1500 * 1024L).contains("1.5 MB"))
    }

    @Test
    fun albumPayload_buildAndParse_roundTrip() {
        val testImages = listOf("base64_img1", "base64_img2", "base64_img3", "base64_img4")
        val caption = "Vacances en famille"
        val payload = MediaAttachmentHelper.buildAlbumPayload("album_123", testImages, caption)

        assertTrue(MediaAttachmentHelper.isAlbumPayload(payload))
        assertFalse(MediaAttachmentHelper.isImagePayload(payload))
        assertFalse(MediaAttachmentHelper.isDocPayload(payload))

        val parsed = MediaAttachmentHelper.parseAlbumPayload(payload)
        assertNotNull(parsed)
        assertEquals("album_123", parsed!!.id)
        assertEquals(4, parsed.images.size)
        assertEquals("base64_img1", parsed.images[0])
        assertEquals("base64_img4", parsed.images[3])
        assertEquals(caption, parsed.caption)
    }

    @Test
    fun isPayloadWithinNostrLimit_checksSizeAccurately() {
        val smallPayload = "a".repeat(1000)
        assertTrue(MediaAttachmentHelper.isPayloadWithinNostrLimit(smallPayload))

        val safePayload = "a".repeat(35000)
        assertTrue(MediaAttachmentHelper.isPayloadWithinNostrLimit(safePayload))

        val oversizedPayload = "a".repeat(50000)
        assertFalse(MediaAttachmentHelper.isPayloadWithinNostrLimit(oversizedPayload))
    }

    @Test
    fun audioVoiceHelper_limitsVerification() {
        val smallVoiceBase64 = "a".repeat(20000)
        assertTrue(AudioVoiceHelper.isPayloadWithinNostrLimit(smallVoiceBase64))

        val oversizedVoiceBase64 = "a".repeat(50000)
        assertFalse(AudioVoiceHelper.isPayloadWithinNostrLimit(oversizedVoiceBase64))
    }
}
