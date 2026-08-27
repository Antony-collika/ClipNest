package com.example

import android.content.ClipData
import android.content.Intent
import android.net.Uri

enum class IncomingUriSource {
    DATA,
    CLIP_DATA,
    EXTRA_STREAM,
    EXTRA_STREAM_MULTIPLE,
    EXTRA_TEXT,
    FILE_PICKER
}

data class ExternalDocumentOpenContext(
    val action: String?,
    val mimeType: String?,
    val source: IncomingUriSource,
    val clipDataItemCount: Int,
    val payloadItemCount: Int,
    val flags: Int,
    val hasReadGrant: Boolean,
    val hasPersistableGrant: Boolean
)

data class IncomingOpenRequest(
    val uri: Uri,
    val openContext: ExternalDocumentOpenContext
)

data class IncomingDocumentUri(
    val uri: Uri,
    val source: IncomingUriSource,
    val itemCount: Int = 1
)

/**
 * Resolves the document URI from an external open/share intent.
 *
 * VIEW and EDIT describe a resource directly, so their data URI is preferred.
 * SEND and SEND_MULTIPLE describe payloads, so their stream payload is preferred
 * over data (which may contain unrelated metadata such as a thumbnail URI).
 */
internal fun resolveIncomingDocumentUri(intent: Intent): IncomingDocumentUri? {
    val action = intent.action
    val payloadFirst = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE

    if (!payloadFirst) {
        documentUri(intent.data)?.let { return IncomingDocumentUri(it, IncomingUriSource.DATA) }
    }

    if (payloadFirst) {
        val singleStream = documentUri(parcelableStreamUri(intent))
        if (singleStream != null) {
            return IncomingDocumentUri(singleStream, IncomingUriSource.EXTRA_STREAM)
        }

        val multipleStream = parcelableStreamUris(intent).mapNotNull(::documentUri)
        if (multipleStream.isNotEmpty()) {
            return IncomingDocumentUri(
                uri = multipleStream.first(),
                source = IncomingUriSource.EXTRA_STREAM_MULTIPLE,
                itemCount = multipleStream.size
            )
        }
    }

    clipDataUri(intent.clipData)?.let { return IncomingDocumentUri(it, IncomingUriSource.CLIP_DATA) }

    if (payloadFirst) {
        documentUri(intent.data)?.let { return IncomingDocumentUri(it, IncomingUriSource.DATA) }
    } else {
        val singleStream = documentUri(parcelableStreamUri(intent))
        if (singleStream != null) {
            return IncomingDocumentUri(singleStream, IncomingUriSource.EXTRA_STREAM)
        }

        val multipleStream = parcelableStreamUris(intent).mapNotNull(::documentUri)
        if (multipleStream.isNotEmpty()) {
            return IncomingDocumentUri(
                uri = multipleStream.first(),
                source = IncomingUriSource.EXTRA_STREAM_MULTIPLE,
                itemCount = multipleStream.size
            )
        }
    }

    intent.getStringExtra(Intent.EXTRA_TEXT)
        ?.let { text -> documentUri(Uri.parse(text)) }
        ?.let { documentUri -> return IncomingDocumentUri(documentUri, IncomingUriSource.EXTRA_TEXT) }

    return null
}

private fun parcelableStreamUri(intent: Intent): Uri? {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }
}

private fun parcelableStreamUris(intent: Intent): List<Uri> {
    @Suppress("DEPRECATION")
    return intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
}

private fun clipDataUri(clipData: ClipData?): Uri? {
    if (clipData == null) return null
    for (index in 0 until clipData.itemCount) {
        documentUri(clipData.getItemAt(index).uri)?.let { return it }
    }
    return null
}

private fun documentUri(uri: Uri?): Uri? {
    return uri?.takeIf { current ->
        current.scheme.equals("content", ignoreCase = true) ||
            current.scheme.equals("file", ignoreCase = true)
    }
}

internal fun buildOpenWithDiagnostic(
    uri: Uri,
    context: ExternalDocumentOpenContext?,
    error: Throwable
): String {
    val rootCause = generateSequence(error) { it.cause }.last()
    val source = context?.source?.name ?: IncomingUriSource.FILE_PICKER.name
    val attempts = if (uri.scheme.equals("file", ignoreCase = true)) {
        "java.io.FileInputStream"
    } else {
        "openInputStream, openFileDescriptor, openAssetFileDescriptor, " +
            "openTypedAssetFileDescriptor"
    }
    val reason = when (rootCause) {
        is SecurityException -> "Permission denied by provider"
        is java.io.FileNotFoundException -> "File not found or provider rejected access"
        is IllegalArgumentException -> "Invalid or unsupported URI"
        else -> rootCause.message?.substringBefore("\n")?.take(240)
            ?.takeIf { it.isNotBlank() }
            ?: "Provider could not open the resource"
    }
    val redactedUri = buildString {
        append(uri.scheme ?: "unknown")
        append("://")
        append(uri.authority ?: "[no-authority]")
        append("/[redacted]")
    }
    return buildString {
        appendLine("X-board Open With diagnostic")
        appendLine()
        appendLine("action: ${context?.action ?: "FILE_PICKER"}")
        appendLine("mimeType: ${context?.mimeType ?: "unknown"}")
        appendLine("uriSource: $source")
        appendLine("uriScheme: ${uri.scheme ?: "unknown"}")
        appendLine("uriAuthority: ${uri.authority ?: "[none]"}")
        appendLine("uriPath: [redacted]")
        appendLine("clipDataItemCount: ${context?.clipDataItemCount ?: 0}")
        appendLine("extraStreamItemCount: ${context?.payloadItemCount ?: 0}")
        appendLine("readGrantFlag: ${if (context?.hasReadGrant == true) "present" else "absent"}")
        appendLine("persistableGrantFlag: ${if (context?.hasPersistableGrant == true) "present" else "absent"}")
        appendLine("selectedUri: $redactedUri")
        appendLine("readAttempts: $attempts")
        appendLine("failureType: ${rootCause::class.java.simpleName}")
        appendLine("reason: $reason")
        appendLine("flags: 0x${Integer.toHexString(context?.flags ?: 0)}")
    }
}
