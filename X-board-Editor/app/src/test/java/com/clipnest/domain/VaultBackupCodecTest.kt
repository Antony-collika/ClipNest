package com.clipnest.domain

import com.clipnest.data.model.ClipboardCard
import com.clipnest.data.model.ContentType
import com.clipnest.data.repository.VaultBackupCodec
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
    fun encryptedExport_roundTripsWithSamePassword() {
        val cards = listOf(
            ClipboardCard(
                id = 1L,
                content = "Private note",
                createdAtMillis = 9876L,
                sortOrder = 100L,
                sourceApp = "Internal",
                contentType = ContentType.TEXT,
                pinned = true,
                preview = "Private",
                isSensitive = true
            )
        )

        val encrypted = VaultBackupCodec.encodeEncrypted(cards, "any password")
        val decoded = VaultBackupCodec.decodeEncrypted(encrypted, "any password")

        assertFalse(encrypted.contains("Private note"))
        assertEquals(1, decoded.cards.size)
        assertEquals("Private note", decoded.cards.single().content)
        assertTrue(decoded.cards.single().isSensitive)
    }

    @Test
    fun encryptedExport_acceptsUserChosenPasswordWithoutFormatRules() {
        val encrypted = VaultBackupCodec.encodeEncrypted(emptyList(), "")

        val decoded = VaultBackupCodec.decodeEncrypted(encrypted, "")

        assertTrue(decoded.cards.isEmpty())
    }

    @Test
    fun encryptedExport_rejectsWrongPassword() {
        val encrypted = VaultBackupCodec.encodeEncrypted(emptyList(), "correct")

        try {
            VaultBackupCodec.decodeEncrypted(encrypted, "wrong")
            org.junit.Assert.fail("Expected wrong password to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: GCM authentication rejects the wrong password.
        }
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
