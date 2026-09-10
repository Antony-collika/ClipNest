package com.clipnest.ui.editor

internal data class ExternalDocumentBlock(
    val displayName: String,
    val content: String
)

internal fun mergeExternalDocumentBlocks(documents: List<ExternalDocumentBlock>): String {
    return documents.mapIndexed { index, document ->
        val fallbackName = "File ${index + 1}"
        val name = document.displayName
            .replace(Regex("[\\r\\n]+"), " ")
            .trim()
            .ifBlank { fallbackName }
        "# $name\n\n${document.content.trimEnd()}"
    }.joinToString("\n\n---\n\n")
}
