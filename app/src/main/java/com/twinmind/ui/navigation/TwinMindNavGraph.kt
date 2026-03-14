package com.twinmind.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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
            // DashboardScreen(navController) — coming soon
        }
        composable(Screen.Recording.route) {
            // RecordingScreen(navController) — coming soon
        }
        composable(Screen.Summary.route) {
            // SummaryScreen(navController) — coming soon
        }
    }
}