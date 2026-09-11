package androidx.compose.material3

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

typealias DialogProperties = androidx.compose.ui.window.DialogProperties

/** Compose-surface bridge for the BorderStroke(width, Color) call used by the editor UI. */
fun BorderStroke(width: Dp, color: Color): androidx.compose.foundation.BorderStroke =
    androidx.compose.foundation.BorderStroke(width, Brush.linearGradient(listOf(color, color)))

@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
        content = content
    )
}
