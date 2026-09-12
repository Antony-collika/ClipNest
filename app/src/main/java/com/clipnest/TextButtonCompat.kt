package com.clipnest

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.TextButton as Material3TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Material3TextButton(
        onClick = onClick,
        modifier = modifier,
        content = content
    )
}
