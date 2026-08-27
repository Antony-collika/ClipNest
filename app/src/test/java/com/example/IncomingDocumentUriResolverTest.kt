package com.example

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.assertNull
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class IncomingDocumentUriResolverTest {
    private val dataUri = Uri.parse("content://provider/metadata")
    private val streamUri = Uri.parse("content://provider/document.md")
    private val clipUri = Uri.parse("content://provider/clip.md")

    @Test
    fun viewPrefersDataUri() {
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(dataUri)
            .putExtra(Intent.EXTRA_STREAM, streamUri)

        val resolved = resolveIncomingDocumentUri(intent)

        assertEquals(dataUri, resolved?.uri)
        assertEquals(IncomingUriSource.DATA, resolved?.source)
    }

    @Test
    fun sendPrefersExtraStreamOverDataUri() {
        val intent = Intent(Intent.ACTION_SEND)
            .setData(dataUri)
            .putExtra(Intent.EXTRA_STREAM, streamUri)

        val resolved = resolveIncomingDocumentUri(intent)

        assertEquals(streamUri, resolved?.uri)
        assertEquals(IncomingUriSource.EXTRA_STREAM, resolved?.source)
    }

    @Test
    fun sendUsesClipDataWhenExtraStreamIsAbsent() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            clipData = ClipData.newRawUri("document", clipUri)
        }

        val resolved = resolveIncomingDocumentUri(intent)

        assertEquals(clipUri, resolved?.uri)
        assertEquals(IncomingUriSource.CLIP_DATA, resolved?.source)
    }

    @Test
    fun sendMultiplePrefersParcelableArrayListAndReportsCount() {
        val secondUri = Uri.parse("content://provider/second.md")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(streamUri, secondUri))
            data = dataUri
        }

        val resolved = resolveIncomingDocumentUri(intent)

        assertEquals(streamUri, resolved?.uri)
        assertEquals(IncomingUriSource.EXTRA_STREAM_MULTIPLE, resolved?.source)
        assertEquals(2, resolved?.itemCount)
    }

    @Test
    fun sendFallsBackToDataOnlyWhenPayloadSourcesAreAbsent() {
        val intent = Intent(Intent.ACTION_SEND).setData(dataUri)

        val resolved = resolveIncomingDocumentUri(intent)

        assertEquals(dataUri, resolved?.uri)
        assertEquals(IncomingUriSource.DATA, resolved?.source)
    }

    @Test
    fun diagnosticRedactsPathAndExplainsPermissionFailure() {
        val uri = Uri.parse("content://private.provider/secret/path.md?token=do-not-copy")
        val context = ExternalDocumentOpenContext(
            action = Intent.ACTION_SEND,
            mimeType = "text/markdown",
            source = IncomingUriSource.EXTRA_STREAM,
            clipDataItemCount = 0,
            payloadItemCount = 1,
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION,
            hasReadGrant = true,
            hasPersistableGrant = false
        )

        val report = buildOpenWithDiagnostic(
            uri,
            context,
            SecurityException("Permission denied for $uri")
        )

        assertTrue(report.contains("uriSource: EXTRA_STREAM"))
        assertTrue(report.contains("reason: Permission denied by provider"))
        assertTrue(report.contains("uriAuthority: private.provider"))
        assertFalse(report.contains("secret/path.md"))
        assertFalse(report.contains("do-not-copy"))
        assertFalse(report.contains("Permission denied for $uri"))
    }

    @Test
    fun ordinaryTextAndUnsupportedUriAreNotDocumentUris() {
        val ordinaryText = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "hello")
        val httpUri = Intent(Intent.ACTION_SEND).setData(Uri.parse("https://example.com/file.md"))

        assertNull(resolveIncomingDocumentUri(ordinaryText))
        assertNull(resolveIncomingDocumentUri(httpUri))
    }
}
