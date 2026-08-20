package com.vasu.codeagent.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vasu.codeagent.VasuApp
import com.vasu.codeagent.ui.chat.ChatScreen
import com.vasu.codeagent.ui.chat.ChatViewModel
import com.vasu.codeagent.ui.home.HomeScreen
import com.vasu.codeagent.ui.settings.SettingsScreen
import com.vasu.codeagent.ui.settings.SettingsViewModel

@Composable
fun VasuNavGraph(app: VasuApp) {
    val navController = rememberNavController()
    val items = listOf(VasuDestination.Home, VasuDestination.Chat, VasuDestination.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                items.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val icon = when (dest) {
                                VasuDestination.Home -> Icons.Filled.Home
                                VasuDestination.Chat -> Icons.Filled.Chat
                                VasuDestination.Settings -> Icons.Filled.Settings
                            }
                            Icon(icon, contentDescription = dest.label)
                        },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = VasuDestination.Home.route,
            modifier = androidx.compose.ui.Modifier.padding(innerPadding),
        ) {
            composable(VasuDestination.Home.route) {
                HomeScreen(
                    app = app,
                    onStartAgent = { navController.navigate(VasuDestination.Chat.route) },
                    onOpenSettings = { navController.navigate(VasuDestination.Settings.route) },
                )
            }
            composable(VasuDestination.Chat.route) {
                val vm: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = ChatViewModel.factory(app),
                )
                ChatScreen(viewModel = vm)
            }
            composable(VasuDestination.Settings.route) {
                val vm: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = SettingsViewModel.factory(app),
                )
                SettingsScreen(viewModel = vm)
            }
        }
    }
}
