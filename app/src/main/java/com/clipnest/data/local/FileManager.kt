package com.clipnest.data.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.clipnest.data.repository.EncryptedDocumentCodec
import java.io.File
import java.nio.charset.StandardCharsets

enum class ExportFormat(val extension: String, val displayName: String, val mimeType: String) {
    MARKDOWN(".md", "Markdown (.md)", "text/markdown"),
    PLAIN_TEXT(".txt", "Plain text (.txt)", "text/plain");

    // The password is supplied only for the current save operation and is cleared by FileManager after use.
    var encryptionPassword: String? = null

    val isEncrypted: Boolean
        get() = !encryptionPassword.isNullOrEmpty()
}

class FileManager(private val context: android.content.Context) {

    private val documentsDir: File
        get() {
            val dir = File(context.filesDir, "documents")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun readEditor(): String {
        val file = File(documentsDir, EDITOR_FILE_NAME)
        return if (file.exists()) {
            file.readText(StandardCharsets.UTF_8)
        } else {
            val defaultContent = "# Note\n\nPersonal workspace for ideas, scratch notes, and tasks.\n"
            file.writeText(defaultContent, StandardCharsets.UTF_8)
            defaultContent
        }
    }

    fun writeEditor(content: String) {
        File(documentsDir, EDITOR_FILE_NAME).writeText(content, StandardCharsets.UTF_8)
    }

    fun readEditorTitle(): String {
        val file = File(documentsDir, EDITOR_TITLE_FILE_NAME)
        return if (file.exists()) file.readText(StandardCharsets.UTF_8) else ""
    }

    fun writeEditorTitle(title: String) {
        File(documentsDir, EDITOR_TITLE_FILE_NAME).writeText(title, StandardCharsets.UTF_8)
    }

    /**
     * Persists the caret position for the internal editor document to disk, so it
     * survives the process being killed (swipe-away, low-memory kill), not just
     * backgrounding. Stored as "start,end"; corrupt or missing files are treated as
     * "no saved position" rather than crashing.
     */
    fun writeEditorCursor(start: Int, end: Int) {
        File(documentsDir, EDITOR_CURSOR_FILE_NAME).writeText("$start,$end", StandardCharsets.UTF_8)
    }

    fun readEditorCursor(): Pair<Int, Int>? {
        val file = File(documentsDir, EDITOR_CURSOR_FILE_NAME)
        if (!file.exists()) return null
        return runCatching {
            val (start, end) = file.readText(StandardCharsets.UTF_8).split(",").map { it.trim().toInt() }
            start to end
        }.getOrNull()
    }

    fun saveNewFile(baseName: String, format: ExportFormat, content: String): File {
        return try {
            val finalName = buildFileName(baseName, format)
            val output = if (format.isEncrypted) {
                val password = requireNotNull(format.encryptionPassword)
                EncryptedDocumentCodec.encode(content, password)
            } else {
                content
            }
            File(documentsDir, finalName).also {
                it.writeText(output, StandardCharsets.UTF_8)
            }
        } finally {
            format.encryptionPassword = null
        }
    }

    fun saveNewFileToTree(
        contentResolver: ContentResolver,
        treeUri: Uri,
        baseName: String,
        format: ExportFormat,
        content: String
    ): Uri? {
        try {
            val parentDocumentUri = if (DocumentsContract.isTreeUri(treeUri)) {
                val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
            } else {
                treeUri
            }
            val encrypted = format.isEncrypted
            val mimeType = if (encrypted) "application/octet-stream" else format.mimeType
            val finalName = buildFileName(baseName, format)
            val documentUri = DocumentsContract.createDocument(
                contentResolver,
                parentDocumentUri,
                mimeType,
                finalName
            ) ?: return null

            val output = if (encrypted) {
                val password = requireNotNull(format.encryptionPassword)
                EncryptedDocumentCodec.encode(content, password)
            } else {
                content
            }
            val bytes = output.toByteArray(StandardCharsets.UTF_8)
            contentResolver.openOutputStream(documentUri, "wt")?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } ?: return null
            return documentUri
        } finally {
            format.encryptionPassword = null
        }
    }

    private fun buildFileName(baseName: String, format: ExportFormat): String {
        val sanitized = sanitizeFileName(baseName)
        val extension = if (format.isEncrypted) ".cne" else format.extension
        return if (sanitized.endsWith(extension, ignoreCase = true)) {
            sanitized
        } else {
            "$sanitized$extension"
        }
    }

    companion object {
        const val EDITOR_FILE_NAME = "Editor.md"
        const val EDITOR_TITLE_FILE_NAME = "Editor.title"
        const val EDITOR_CURSOR_FILE_NAME = "Editor.cursor"

        fun sanitizeFileName(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return "Untitled"
            return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        }
    }
}
