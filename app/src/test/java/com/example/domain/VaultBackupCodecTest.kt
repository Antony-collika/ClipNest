package com.example.domain

import com.example.data.model.ClipboardCard
import com.example.data.model.ContentType
import com.example.data.repository.VaultBackupCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultBackupCodecTest {

    @Test
    fun encode_containsOnlyUserCentricCardFields() {
        val json = VaultBackupCodec.encode(
            listOf(
                ClipboardCard(
                    id = 99L,
                    content = "Keep this text",
                    createdAtMillis = 1234L,
                    sortOrder = 5000L,
                    sourceApp = "Internal source",
                    contentType = ContentType.TEXT,
                    pinned = true,
                    preview = "Keep this",
                    isSensitive = true
                )
            )
        )

        assertTrue(json.contains("\"content\""))
        assertTrue(json.contains("\"createdAtMillis\""))
        assertTrue(json.contains("\"pinned\""))
        assertTrue(json.contains("\"isSensitive\""))
        assertFalse(json.contains("\"id\""))
        assertFalse(json.contains("\"sortOrder\""))
        assertFalse(json.contains("\"sourceApp\""))
        assertFalse(json.contains("\"contentType\""))
        assertFalse(json.contains("\"preview\""))
    }

    @Test
    fun decode_roundTripsUserCentricFields() {
        val json = """
            {
              "format": "xboard-backup",
              "version": 1,
              "cards": [
                {
                  "content": "https://example.com",
                  "createdAtMillis": 1234,
                  "pinned": true,
                  "isSensitive": false
                }
              ]
            }
        """.trimIndent()

        val backup = VaultBackupCodec.decode(json)

        assertEquals(1, backup.cards.size)
        assertEquals("https://example.com", backup.cards.single().content)
        assertEquals(1234L, backup.cards.single().createdAtMillis)
        assertTrue(backup.cards.single().pinned)
        assertFalse(backup.cards.single().isSensitive)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decode_rejectsUnknownFormat() {
        VaultBackupCodec.decode("""
            {"format":"other-app","version":1,"cards":[]}
        """.trimIndent())
    }
}
