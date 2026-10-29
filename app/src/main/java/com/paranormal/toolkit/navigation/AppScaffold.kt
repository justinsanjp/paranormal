package com.paranormal.toolkit.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.paranormal.toolkit.features.analyzer.AnalyzerScreen
import com.paranormal.toolkit.features.ghostapp.GhostAppScreen
import com.paranormal.toolkit.features.ghostbox.GhostBoxScreen
import com.paranormal.toolkit.features.recorder.RecorderScreen

@Composable
fun AppScaffold() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                Screen.bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Recorder.route,
            modifier = Modifier,
        ) {
            composable(Screen.Recorder.route) { RecorderScreen(contentPadding = innerPadding) }
            composable(Screen.Analyzer.route) { AnalyzerScreen(contentPadding = innerPadding) }
            composable(Screen.GhostBox.route) { GhostBoxScreen(contentPadding = innerPadding) }
            composable(Screen.GhostApp.route) { GhostAppScreen(contentPadding = innerPadding) }
        }
    }
}
