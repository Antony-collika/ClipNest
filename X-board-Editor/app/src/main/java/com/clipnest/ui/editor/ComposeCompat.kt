package com.clipnest.ui.editor

import androidx.compose.foundation.gestures.detectTapGestures as foundationDetectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight as foundationFillMaxHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope

/** Bridges used by EditorScreen while keeping the app compatible with its Compose surface. */
fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier =
    this.foundationFillMaxHeight(fraction)

suspend fun PointerInputScope.detectTapGestures(
    onTap: (Offset) -> Unit
) {
    foundationDetectTapGestures(onTap = onTap)
}
