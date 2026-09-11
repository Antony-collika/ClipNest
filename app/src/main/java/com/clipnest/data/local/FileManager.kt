package com.clipnest.data.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.nio.charset.StandardCharsets

enum class ExportFormat(val extension: String, val displayName: String, val mimeType: String) {
    MARKDOWN(".md", "Markdown (.md)", "text/markdown"),
    PLAIN_TEXT(".txt", "Plain text (.txt)", "text/plain")
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

    fun saveNewFile(baseName: String, format: ExportFormat, content: String): File {
        val finalName = buildFileName(baseName, format)
        return File(documentsDir, finalName).also {
            it.writeText(content, StandardCharsets.UTF_8)
        }
    }

    fun saveNewFileToTree(
        contentResolver: ContentResolver,
        treeUri: Uri,
        baseName: String,
        format: ExportFormat,
        content: String
    ): Uri? {
        val parentDocumentUri = if (DocumentsContract.isTreeUri(treeUri)) {
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        } else {
            treeUri
        }
        val documentUri = DocumentsContract.createDocument(
            contentResolver,
            parentDocumentUri,
            format.mimeType,
            buildFileName(baseName, format)
        ) ?: return null

        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        contentResolver.openOutputStream(documentUri, "wt")?.use { output ->
            output.write(bytes)
            output.flush()
        } ?: return null
        return documentUri
    }

    fun listExportedFiles(): List<File> {
        return documentsDir.listFiles()
            ?.filter { it.isFile && it.name != EDITOR_FILE_NAME }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun buildFileName(baseName: String, format: ExportFormat): String {
        val sanitized = sanitizeFileName(baseName)
        return if (sanitized.endsWith(format.extension, ignoreCase = true)) {
            sanitized
        } else {
            "$sanitized${format.extension}"
        }
    }

    companion object {
        const val EDITOR_FILE_NAME = "Editor.md"

        fun sanitizeFileName(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return "Untitled"
            return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        }
    }
}
