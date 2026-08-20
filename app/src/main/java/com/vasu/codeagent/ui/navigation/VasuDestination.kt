package com.vasu.codeagent.ui.navigation

sealed class VasuDestination(val route: String, val label: String) {
    data object Home : VasuDestination("home", "Home")
    data object Chat : VasuDestination("chat", "Agent")
    data object Settings : VasuDestination("settings", "Settings")
}
