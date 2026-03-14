package com.twinmind.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.twinmind.ui.dashboard.DashboardScreen
import com.twinmind.ui.recording.RecordingScreen
import com.twinmind.ui.summary.SummaryScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Recording : Screen("recording/{meetingId}") {
        fun createRoute(meetingId: String) = "recording/$meetingId"
    }
    object Summary : Screen("summary/{meetingId}") {
        fun createRoute(meetingId: String) = "summary/$meetingId"
    }
}

@Composable
fun TwinMindNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController)
        }
        composable(
            route = Screen.Recording.route,
            arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
        ) { backStack ->
            val meetingId = backStack.arguments?.getString("meetingId") ?: ""
            RecordingScreen(navController = navController, meetingId = meetingId)
        }
        composable(
            route = Screen.Summary.route,
            arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
        ) { backStack ->
            val meetingId = backStack.arguments?.getString("meetingId") ?: ""
            SummaryScreen(navController = navController, meetingId = meetingId)
        }
    }
}