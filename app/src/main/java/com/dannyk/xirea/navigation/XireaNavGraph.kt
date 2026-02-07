package com.dannyk.xirea.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dannyk.xirea.XireaApplication
import com.dannyk.xirea.ui.about.AboutScreen
import com.dannyk.xirea.ui.chat.ChatScreen
import com.dannyk.xirea.ui.chat.ChatViewModel
import com.dannyk.xirea.ui.home.HomeScreen
import com.dannyk.xirea.ui.home.HomeViewModel
import com.dannyk.xirea.ui.models.ModelsScreen
import com.dannyk.xirea.ui.models.ModelsViewModel
import com.dannyk.xirea.ui.settings.SettingsScreen
import com.dannyk.xirea.ui.settings.SettingsViewModel

@Composable
fun XireaNavGraph(
    navController: NavHostController
) {
    val context = LocalContext.current
    val app = context.applicationContext as XireaApplication
    
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // Home Screen
        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(
                    chatRepository = app.chatRepository,
                    aiEngine = app.aiEngine
                )
            )
            
            HomeScreen(
                viewModel = viewModel,
                onNavigateToChat = { chatId ->
                    navController.navigate(Screen.Chat.createRoute(chatId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToModels = {
                    navController.navigate(Screen.Models.route)
                }
            )
        }
        
        // Chat Screen (existing chat)
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getLong("chatId")
            
            val viewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(
                    chatRepository = app.chatRepository,
                    modelRepository = app.modelRepository,
                    aiEngine = app.aiEngine
                )
            )
            
            ChatScreen(
                viewModel = viewModel,
                chatId = chatId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate(Screen.Models.route) }
            )
        }
        
        // New Chat Screen
        composable(Screen.NewChat.route) {
            val viewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(
                    chatRepository = app.chatRepository,
                    modelRepository = app.modelRepository,
                    aiEngine = app.aiEngine
                )
            )
            
            ChatScreen(
                viewModel = viewModel,
                chatId = null,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate(Screen.Models.route) }
            )
        }
        
        // Models Screen
        composable(Screen.Models.route) {
            val viewModel: ModelsViewModel = viewModel(
                factory = ModelsViewModel.Factory(
                    modelRepository = app.modelRepository,
                    userPreferences = app.userPreferences,
                    aiEngine = app.aiEngine,
                    context = context
                )
            )
            
            ModelsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Settings Screen
        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(
                    userPreferences = app.userPreferences,
                    chatRepository = app.chatRepository,
                    modelRepository = app.modelRepository,
                    aiEngine = app.aiEngine
                )
            )
            
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModels = { navController.navigate(Screen.Models.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) }
            )
        }
        
        // About Screen
        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
