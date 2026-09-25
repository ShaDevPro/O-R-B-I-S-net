package com.sha.orbis.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MessageRevocationTest {
    @Test
    fun parse_supportsLegacyPacket() {
        val parsed = MessageRevocation.parse("[REVOKE:msg_123]")

        assertNotNull(parsed)
        assertEquals("msg_123", parsed!!.messageId)
    }

    @Test
    fun findMatchIndex_matchesByNetworkEventId() {
        val target = MessageRevocationTarget(messageId = "sender_local_id", networkEventId = "nostr_event_id")
        val messages = listOf(message("receiver_local_id", networkEventId = "nostr_event_id"))

        assertEquals(0, MessageRevocation.findMatchIndex(messages, target))
    }

    @Test
    fun findMatchIndex_matchesVideoByMediaIdWhenMessageIdsDiffer() {
        val target = MessageRevocationTarget(messageId = "msg_sender", mediaId = "vid_42")
        val messages = listOf(message("msg_receiver", text = "[VIDEO:vid_42|1000|720|1280|https://cdn/video.mp4]"))

        assertEquals(0, MessageRevocation.findMatchIndex(messages, target))
    }

    @Test
    fun encodeAndParse_keepsContentHashForTextFallback() {
        val original = message("msg_sender", text = "Bonjour", timestamp = 1_000L)
        val parsed = MessageRevocation.parse(MessageRevocation.encode(original))
        val receiver = message("msg_receiver", text = "Bonjour", timestamp = 1_250L)

        assertNotNull(parsed)
        assertEquals(0, MessageRevocation.findMatchIndex(listOf(receiver), parsed!!))
    }

    private fun message(
        id: String,
        text: String = "hello",
        timestamp: Long = 10_000L,
        networkEventId: String? = null
    ): Message = Message(
        id = id,
        conversationId = "conv_test",
        senderId = "sender",
        text = text,
        timestamp = timestamp,
        networkEventId = networkEventId
    )
}
