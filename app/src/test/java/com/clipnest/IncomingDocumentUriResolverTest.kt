package com.clipnest

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import com.clipnest.ui.editor.ExternalDocumentBlock
import com.clipnest.ui.editor.mergeExternalDocumentBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertNull
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
    fun viewKeepsPayloadUriAsFallbackAfterDataUri() {
        val intent = Intent(Intent.ACTION_VIEW)
            .setData(dataUri)
            .putExtra(Intent.EXTRA_STREAM, streamUri)

        val candidates = resolveIncomingDocumentUris(intent)

        assertEquals(listOf(dataUri, streamUri), candidates.map { it.uri })
        assertEquals(IncomingUriSource.DATA, candidates[0].source)
        assertEquals(IncomingUriSource.EXTRA_STREAM, candidates[1].source)
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
    fun sendMultipleReturnsAllParcelableUrisAndReportsCount() {
        val secondUri = Uri.parse("content://provider/second.md")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(streamUri, secondUri))
            data = dataUri
        }

        val resolved = resolveIncomingDocumentUris(intent)

        assertEquals(listOf(streamUri, secondUri), resolved.map { it.uri })
        assertTrue(resolved.all { it.source == IncomingUriSource.EXTRA_STREAM_MULTIPLE })
        assertTrue(resolved.all { it.itemCount == 2 })
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
    fun sendMultipleClipDataReturnsAllDocumentUris() {
        val secondUri = Uri.parse("content://provider/second.txt")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            clipData = ClipData.newRawUri("first", streamUri).apply {
                addItem(ClipData.Item(secondUri))
            }
        }

        val resolved = resolveIncomingDocumentUris(intent)

        assertEquals(listOf(streamUri, secondUri), resolved.map { it.uri })
        assertTrue(resolved.all { it.source == IncomingUriSource.CLIP_DATA })
    }

    @Test
    fun mergeUsesHeadingBlankLinesAndSeparators() {
        val merged = mergeExternalDocumentBlocks(
            listOf(
                ExternalDocumentBlock("one.md", "alpha\n"),
                ExternalDocumentBlock("two.txt", "beta")
            )
        )

        assertEquals("# one.md\n\nalpha\n\n---\n\n# two.txt\n\nbeta", merged)
    }

    @Test
    fun ordinaryTextAndUnsupportedUriAreNotDocumentUris() {
        val ordinaryText = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "hello")
        val httpUri = Intent(Intent.ACTION_SEND).setData(Uri.parse("https://example.com/file.md"))

        assertNull(resolveIncomingDocumentUri(ordinaryText))
        assertNull(resolveIncomingDocumentUri(httpUri))
    }
}
