package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Vault : Screen(
        route = "vault",
        title = "Kho",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder
    )

    data object Note : Screen(
        route = "note",
        title = "Soạn thảo",
        selectedIcon = Icons.Filled.Description,
        unselectedIcon = Icons.Outlined.Description
    )

    data object Settings : Screen(
        route = "settings",
        title = "Cài đặt",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val items = listOf(Vault, Note, Settings)
    }
}
