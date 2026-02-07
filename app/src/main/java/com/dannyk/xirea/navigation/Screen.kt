package com.dannyk.xirea.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat/{chatId}") {
        fun createRoute(chatId: Long) = "chat/$chatId"
    }
    object NewChat : Screen("chat/new")
    object Models : Screen("models")
    object Settings : Screen("settings")
    object About : Screen("about")
}
