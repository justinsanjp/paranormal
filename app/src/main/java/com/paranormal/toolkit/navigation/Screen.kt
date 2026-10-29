package com.paranormal.toolkit.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Recorder : Screen("recorder", "EVP Recorder", Icons.Filled.Mic)
    data object Analyzer : Screen("analyzer", "Analyzer", Icons.Filled.Analytics)
    data object GhostBox : Screen("ghostbox", "GhostBox", Icons.Filled.Radio)
    data object GhostApp : Screen("ghostapp", "GhostApp", Icons.AutoMirrored.Filled.Chat)

    companion object {
        val bottomNavItems = listOf(Recorder, Analyzer, GhostBox, GhostApp)
    }
}
