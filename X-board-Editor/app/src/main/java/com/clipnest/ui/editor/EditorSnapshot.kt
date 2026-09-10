package com.clipnest.ui.editor

/**
 * Immutable snapshot captured at an explicit boundary such as preview or I/O.
 *
 * Normal editing/search keeps using [EditorDocument.charSequence]. A snapshot
 * is deliberately a String so callers must opt into whole-document materialisation.
 */
data class EditorSnapshot(
    val revision: Long,
    val text: String
)

/**
 * Captures the current document once. Callers should pass the returned revision
 * through async work and only publish the result if it is still current.
 */
fun EditorDocument.snapshot(revision: Long): EditorSnapshot =
    EditorSnapshot(revision = revision, text = snapshot())

fun EditorSnapshot.isCurrent(currentRevision: Long): Boolean = revision == currentRevision
