package com.clipnest.ui.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset

/**
 * Compatibility implementation for the IntOffset lambda form of Modifier.offset.
 * Keeps the preview popup movement independent from the Compose foundation import set.
 */
fun Modifier.offset(offset: () -> IntOffset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val value = offset()
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(value.x, value.y)
    }
}
