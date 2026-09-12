package com.clipnest

import androidx.compose.ui.Modifier

/**
 * Fallback for legacy search-field code that calls weight outside a RowScope.
 * RowScope's real weight extension still wins wherever a RowScope receiver exists.
 */
fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier = this
