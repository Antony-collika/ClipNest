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
    val extraStreamPresent: Boolean = false,
    val extraStreamValueType: String? = null,
    val flags: Int,
    val hasReadGrant: Boolean,
    val hasPersistableGrant: Boolean
)

data class IncomingDocumentUri(
    val uri: Uri,
    val source: IncomingUriSource,
    val itemCount: Int = 1
)

data class IncomingOpenRequest(
    val uri: Uri,
    val openContext: ExternalDocumentOpenContext,
    val candidates: List<IncomingDocumentUri> = listOf(
        IncomingDocumentUri(uri, openContext.source)
    )
)

data class ExternalDocumentReadFailure(
    val candidate: IncomingDocumentUri,
    val error: Throwable
)

data class IncomingStreamInfo(
    val present: Boolean,
    val valueType: String?,
    val documentUriCount: Int
)

/**
 * Resolves all document URI candidates from an incoming Intent.
 *
 * VIEW and EDIT describe a resource directly, so data is the primary candidate.
 * SEND and SEND_MULTIPLE describe payloads, so every stream/ClipData URI is
 * retained in sender order; data is only a fallback for senders that populate it.
 */
fun resolveIncomingDocumentUris(intent: Intent): List<IncomingDocumentUri> {
    val candidates = mutableListOf<IncomingDocumentUri>()
    val payloadFirst = intent.action == Intent.ACTION_SEND ||
        intent.action == Intent.ACTION_SEND_MULTIPLE

    fun add(uri: Uri?, source: IncomingUriSource, itemCount: Int = 1) {
        val validUri = documentUri(uri) ?: return
        if (candidates.none { it.uri == validUri }) {
            candidates += IncomingDocumentUri(validUri, source, itemCount)
        }
    }

    fun addAll(uris: List<Uri>, source: IncomingUriSource) {
        val itemCount = uris.size.coerceAtLeast(1)
        uris.forEach { add(it, source, itemCount) }
    }

    if (!payloadFirst) {
        add(intent.data, IncomingUriSource.DATA)
    }

    val streamUris = documentStreamUris(intent)
    val clipUris = clipDataDocumentUris(intent.clipData)
    if (payloadFirst) {
        if (streamUris.size == 1) {
            add(streamUris.first(), IncomingUriSource.EXTRA_STREAM)
        } else if (streamUris.size > 1) {
            addAll(streamUris, IncomingUriSource.EXTRA_STREAM_MULTIPLE)
        }
        addAll(clipUris, IncomingUriSource.CLIP_DATA)
        if (streamUris.isEmpty() && clipUris.isEmpty()) {
            add(intent.data, IncomingUriSource.DATA)
        }
    } else {
        addAll(clipUris.take(1), IncomingUriSource.CLIP_DATA)
        if (streamUris.isNotEmpty()) {
            add(streamUris.first(), IncomingUriSource.EXTRA_STREAM)
        }
    }

    intent.getStringExtra(Intent.EXTRA_TEXT)
        ?.let { text -> documentUri(Uri.parse(text)) }
        ?.let { add(it, IncomingUriSource.EXTRA_TEXT) }

    return candidates
}

fun resolveIncomingDocumentUri(intent: Intent): IncomingDocumentUri? =
    resolveIncomingDocumentUris(intent).firstOrNull()

fun inspectIncomingExtraStream(intent: Intent): IncomingStreamInfo {
    val raw = runCatching { intent.extras?.get(Intent.EXTRA_STREAM) }.getOrNull()
    val rawValues = rawStreamValues(raw)
    return IncomingStreamInfo(
        present = intent.hasExtra(Intent.EXTRA_STREAM),
        valueType = raw?.javaClass?.simpleName,
        documentUriCount = rawValues.count { streamValueToDocumentUri(it) != null }
    )
}

private fun documentStreamUris(intent: Intent): List<Uri> {
    val raw = runCatching { intent.extras?.get(Intent.EXTRA_STREAM) }.getOrNull()
    return rawStreamValues(raw).mapNotNull(::streamValueToDocumentUri)
}

private fun rawStreamValues(raw: Any?): List<Any?> = when (raw) {
    is ArrayList<*> -> raw
    is List<*> -> raw
    null -> emptyList()
    else -> listOf(raw)
}

private fun streamValueToDocumentUri(value: Any?): Uri? {
    return when (value) {
        is Uri -> documentUri(value)
        is String -> documentUri(Uri.parse(value))
        else -> null
    }
}

private fun clipDataDocumentUris(clipData: ClipData?): List<Uri> {
    if (clipData == null) return emptyList()
    return (0 until clipData.itemCount).mapNotNull { index ->
        documentUri(clipData.getItemAt(index).uri)
    }
}

private fun documentUri(uri: Uri?): Uri? {
    return uri?.takeIf { current ->
        current.scheme.equals("content", ignoreCase = true) ||
            current.scheme.equals("file", ignoreCase = true)
    }
}

fun buildOpenWithDiagnostic(
    uri: Uri,
    context: ExternalDocumentOpenContext?,
    error: Throwable
): String = buildOpenWithDiagnostic(
    failures = listOf(
        ExternalDocumentReadFailure(
            IncomingDocumentUri(uri, context?.source ?: IncomingUriSource.FILE_PICKER),
            error
        )
    ),
    context = context
)

fun buildOpenWithDiagnostic(
    failures: List<ExternalDocumentReadFailure>,
    context: ExternalDocumentOpenContext?
): String {
    val primaryFailure = failures.firstOrNull()
    val primaryUri = primaryFailure?.candidate?.uri
    val error = primaryFailure?.error
    val rootCause = error?.let { generateSequence(it) { cause -> cause.cause }.last() }
    val source = primaryFailure?.candidate?.source?.name
        ?: context?.source?.name
        ?: IncomingUriSource.FILE_PICKER.name
    val attempts = if (primaryUri?.scheme.equals("file", ignoreCase = true)) {
        "ContentResolver.openInputStream, java.io.FileInputStream"
    } else {
        "openInputStream, openFileDescriptor, openAssetFileDescriptor, " +
            "openTypedAssetFileDescriptor"
    }
    val reason = when (rootCause) {
        is SecurityException -> "Permission denied by provider"
        is java.io.FileNotFoundException -> "File not found or provider rejected access"
        is IllegalArgumentException -> "Invalid or unsupported URI"
        else -> rootCause?.message?.substringBefore("\n")?.take(240)
            ?.takeIf { it.isNotBlank() }
            ?: "Provider could not open the resource"
    }
    val redactedUri = primaryUri?.let { uri ->
        buildString {
            append(uri.scheme ?: "unknown")
            append("://")
            append(uri.authority ?: "[no-authority]")
            append("/[redacted]")
        }
    } ?: "[unavailable]"
    return buildString {
        appendLine("X-board Open With diagnostic")
        appendLine()
        appendLine("action: ${context?.action ?: "FILE_PICKER"}")
        appendLine("mimeType: ${context?.mimeType ?: "unknown"}")
        appendLine("uriSource: $source")
        appendLine("uriScheme: ${primaryUri?.scheme ?: "unknown"}")
        appendLine("uriAuthority: ${primaryUri?.authority ?: "[none]"}")
        appendLine("uriPath: [redacted]")
        appendLine("clipDataItemCount: ${context?.clipDataItemCount ?: 0}")
        appendLine("extraStreamPresent: ${if (context?.extraStreamPresent == true) "yes" else "no"}")
        appendLine("extraStreamValueType: ${context?.extraStreamValueType ?: "none"}")
        appendLine("extraStreamItemCount: ${context?.payloadItemCount ?: 0}")
        appendLine("readGrantFlag: ${if (context?.hasReadGrant == true) "present" else "absent"}")
        appendLine("persistableGrantFlag: ${if (context?.hasPersistableGrant == true) "present" else "absent"}")
        appendLine("selectedUri: $redactedUri")
        appendLine("candidateCount: ${failures.size}")
        appendLine("readAttempts: $attempts")
        appendLine("failureType: ${rootCause?.javaClass?.simpleName ?: "Unknown"}")
        appendLine("reason: $reason")
        appendLine("flags: 0x${Integer.toHexString(context?.flags ?: 0)}")
        if (failures.size > 1) {
            appendLine("candidateSources: ${failures.joinToString(",") { it.candidate.source.name }}")
        }
    }
}
