package com.example.irondiary.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable

sealed class Screen(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
) {
    object Calendar : Screen("calendar", "Calendar", { Icon(Icons.Filled.DateRange, contentDescription = null) })
    object Academics : Screen("academics", "Academics", { Icon(Icons.Filled.School, contentDescription = null) })
    object Graph : Screen("graph", "Graph", { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) })
    object Templates : Screen("templates", "Templates", { Icon(Icons.Filled.Done, contentDescription = null) })
}

val bottomNavItems = listOf(
    Screen.Calendar,
    Screen.Academics,
    Screen.Graph,
    Screen.Templates,
)
