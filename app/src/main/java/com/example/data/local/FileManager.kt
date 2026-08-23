package com.example.data.local

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets

enum class ExportFormat(val extension: String, val displayName: String, val mimeType: String) {
    MARKDOWN(".md", "Markdown (.md)", "text/markdown"),
    PLAIN_TEXT(".txt", "Plain text (.txt)", "text/plain")
}

class FileManager(private val context: Context) {

    private val documentsDir: File
        get() {
            val dir = File(context.filesDir, "documents")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    fun readNote(docType: NoteDocType): String {
        val file = File(documentsDir, docType.fileName)
        return if (file.exists()) {
            file.readText(StandardCharsets.UTF_8)
        } else {
            val defaultContent = when (docType) {
                NoteDocType.NOTE -> "# Note\n\nPersonal workspace for ideas, scratch notes, and tasks.\n"
                NoteDocType.DRAFT -> "# Draft\n\nWorking workspace for clipboard composition and quick editing.\n"
            }
            writeNote(docType, defaultContent)
            defaultContent
        }
    }

    fun writeNote(docType: NoteDocType, content: String) {
        val file = File(documentsDir, docType.fileName)
        file.writeText(content, StandardCharsets.UTF_8)
    }

    fun saveNewFile(baseName: String, format: ExportFormat, content: String): File {
        val sanitized = sanitizeFileName(baseName)
        val finalName = if (sanitized.endsWith(format.extension, ignoreCase = true)) {
            sanitized
        } else {
            "$sanitized${format.extension}"
        }
        val file = File(documentsDir, finalName)
        file.writeText(content, StandardCharsets.UTF_8)
        return file
    }

    fun listExportedFiles(): List<File> {
        return documentsDir.listFiles()
            ?.filter { it.isFile && it.name != NoteDocType.NOTE.fileName && it.name != NoteDocType.DRAFT.fileName }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    companion object {
        fun sanitizeFileName(name: String): String {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return "Untitled"
            return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        }
    }
}
