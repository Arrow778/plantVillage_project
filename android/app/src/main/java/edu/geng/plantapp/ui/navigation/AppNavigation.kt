package edu.geng.plantapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import edu.geng.plantapp.ui.screens.auth.LoginScreen
import edu.geng.plantapp.ui.screens.auth.RegisterScreen
import edu.geng.plantapp.ui.screens.home.HomeScreen
import edu.geng.plantapp.ui.screens.result.ResultScreen
import edu.geng.plantapp.ui.screens.profile.ProfileScreen
import java.net.URLEncoder
import java.net.URLDecoder

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true } // 清除登录页的历史栈，防止用户按返回键又退回登录
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
        }
        composable("register") {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.popBackStack() // Back to login
                },
                onNavigateBackToLogin = {
                    navController.popBackStack()
                }
            )
        }
        composable("home") {
            HomeScreen(
                onNavigateToResult = { diseaseLabel, imageUri -> 
                    val encodedLabel = URLEncoder.encode(diseaseLabel, "UTF-8")
                    val encodedUri = if (imageUri != null) URLEncoder.encode(imageUri, "UTF-8") else "null"
                    navController.navigate("result/${encodedLabel}?imageUri=${encodedUri}")
                },
                onNavigateToProfile = {
                    navController.navigate("profile")
                },
                onNavigateToHistory = {
                    navController.navigate("history")
                }
            )
        }
        composable("history") {
            edu.geng.plantapp.ui.screens.history.HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToResult = { diseaseLabel, imageUri, historyId ->
                    val encodedLabel = URLEncoder.encode(diseaseLabel, "UTF-8")
                    val encodedUri = if (imageUri != null) URLEncoder.encode(imageUri, "UTF-8") else "null"
                    navController.navigate("result/${encodedLabel}?imageUri=${encodedUri}&historyId=${historyId}")
                }
            )
        }
        composable("result/{diseaseLabel}?imageUri={imageUri}&historyId={historyId}") { backStackEntry ->
            val diseaseLabel = backStackEntry.arguments?.getString("diseaseLabel") ?: "Unknown"
            val rawImageUri = backStackEntry.arguments?.getString("imageUri")
            val imageUri = if (rawImageUri != null && rawImageUri != "null") URLDecoder.decode(rawImageUri, "UTF-8") else null
            val historyId = backStackEntry.arguments?.getString("historyId")?.toIntOrNull() ?: -1
            ResultScreen(
                diseaseLabel = diseaseLabel,
                imageUri = imageUri,
                historyId = historyId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("profile") {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogoutSuccess = {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
} 
